package me.blockcraft.api.world;

import me.blockcraft.common.config.YamlFile;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

/**
 * A map kept outside the server. The world folder is replaced by a copy of the template at every start, so
 * nothing a round or a builder left behind survives, and {@link #publish} copies the running world back once a
 * change is meant to stay. Player state and the session lock never travel with a map. Both directions replace the
 * contents of a directory rather than the directory, because either side may be a mount point. Every server that
 * mounts a template takes its lock file for a publish and for a copy, so none reads a template another is swapping.
 */
public final class WorldTemplate {

    private static final String ENV = "BLOCKCRAFT_WORLD_TEMPLATE";
    private static final String LEVEL = "level.dat";
    private static final Set<String> TRANSIENT = Set.of("session.lock", "players", "playerdata", "stats", "advancements", "level.dat_old");
    private static final String STAGING = ".staging";
    private static final String REPLACED = ".replaced";
    private static final String READY = ".publish-ready";
    private static final String LOCK = ".template.lock";
    private static final Set<String> PUBLISHING = Set.of(STAGING, REPLACED, READY, LOCK);
    private static final Duration LOCK_WAIT = Duration.ofMinutes(2);
    private static final Duration LOCK_POLL = Duration.ofMillis(100);
    /**
     * Only the vanilla dimensions belong to a map. Worlds a plugin loads from a pool ({@link TemplateDimensions}) or
     * builds at every start live beside them in the level and would otherwise be published along with it.
     */
    private static final String DIMENSIONS = "dimensions";
    private static final String VANILLA_DIMENSIONS = "minecraft";

    private static String picked;

    private final Path directory;

    private WorldTemplate(Path directory) {
        this.directory = directory;
    }

    public static WorldTemplate at(Path directory) {
        return new WorldTemplate(directory.toAbsolutePath().normalize());
    }

    /**
     * {@code BLOCKCRAFT_WORLD_TEMPLATE}, else {@code world.template} of the config; empty when neither names a directory.
     * Several comma-separated directories rotate maps: one is picked at random and kept for as long as this server runs,
     * so a game server that restarts after each round plays another map next time.
     */
    public static Optional<WorldTemplate> configured(Map<String, Object> config) {
        String env = System.getenv(ENV);
        String path = env != null && !env.isBlank() ? env : String.valueOf(YamlFile.section(config, "world").getOrDefault("template", ""));
        if (picked == null) {
            pick(path, ThreadLocalRandom.current()).ifPresent(choice -> picked = choice);
        }
        return Optional.ofNullable(picked).map(choice -> at(Path.of(choice)));
    }

    static Optional<String> pick(String paths, RandomGenerator random) {
        var choices = Arrays.stream(paths.split(",")).map(String::strip).filter(choice -> !choice.isEmpty()).toList();
        return choices.isEmpty() ? Optional.empty() : Optional.of(choices.get(random.nextInt(choices.size())));
    }

    public Path directory() {
        return directory;
    }

    /** A map is a directory with a level.dat; an empty or missing directory is a template nobody has published to yet. */
    public boolean exists() {
        return Files.isRegularFile(directory.resolve(LEVEL));
    }

    public void restore(Path world) {
        try {
            exclusively(() -> {
                repair();
                Files.createDirectories(world);
                clear(world, name -> true);
                copyContents(directory, world, name -> !PUBLISHING.contains(name));
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + directory + " to " + world, e);
        }
    }

    /**
     * Copies the world into a staging directory inside the template and only then swaps the contents with renames,
     * which stay on the template's file system, so a copy that fails or is cut short leaves the old map in place.
     * A marker records when the old contents have moved out, including for dimensions without level.dat.
     */
    public void publish(Path world) {
        Path staging = directory.resolve(STAGING);
        Path replaced = directory.resolve(REPLACED);
        try {
            Files.createDirectories(directory);
            exclusively(() -> {
                repair();
                Files.createDirectories(staging);
                Path vanilla = world.resolve(DIMENSIONS).resolve(VANILLA_DIMENSIONS);
                if (Files.isDirectory(vanilla)) {
                    copyRecursively(vanilla, Files.createDirectories(staging.resolve(DIMENSIONS)).resolve(VANILLA_DIMENSIONS));
                }
                copyContents(world, staging, name -> !TRANSIENT.contains(name) && !PUBLISHING.contains(name) && !name.equals(DIMENSIONS));
                Files.createDirectories(replaced);
                moveContents(directory, replaced, name -> !PUBLISHING.contains(name));
                Files.createFile(directory.resolve(READY));
                moveContents(staging, directory, name -> true);
                clearPublishing();
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + world + " to " + directory, e);
        }
    }

    /**
     * Leaves the template whole after a publish that stopped partway, before anything reads it. Once the old map moved
     * out completely, the new one is complete in staging and the rest of it moves in; before that, the old contents
     * that already moved out go back. Leftovers of a copy that never got to the swap are dropped.
     */
    public void recover() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try {
            exclusively(this::repair);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not repair " + directory, e);
        }
    }

    private interface FileWork {
        void run() throws IOException;
    }

    private void exclusively(FileWork work) throws IOException {
        try (var channel = FileChannel.open(directory.resolve(LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var _ = lock(channel)) {
            work.run();
        }
    }

    private FileLock lock(FileChannel channel) throws IOException {
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();
        while (true) {
            var lock = tryLock(channel);
            if (lock.isPresent()) {
                return lock.orElseThrow();
            }
            if (System.nanoTime() - deadline > 0) {
                throw new IOException("Another server held " + directory + " for longer than " + LOCK_WAIT);
            }
            try {
                Thread.sleep(LOCK_POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for " + directory);
            }
        }
    }

    /** Empty while another process or another thread of this one holds the lock. */
    private static Optional<FileLock> tryLock(FileChannel channel) throws IOException {
        try {
            return Optional.ofNullable(channel.tryLock());
        } catch (OverlappingFileLockException _) {
            return Optional.empty();
        }
    }

    private void repair() throws IOException {
        Path staging = directory.resolve(STAGING);
        Path replaced = directory.resolve(REPLACED);
        if (Files.isRegularFile(directory.resolve(READY)) || Files.isRegularFile(replaced.resolve(LEVEL))) {
            if (Files.isDirectory(staging)) {
                moveContents(staging, directory, name -> true);
            }
        } else if (Files.isDirectory(replaced)) {
            moveContents(replaced, directory, name -> true);
        }
        if (Files.isDirectory(directory)) {
            clearPublishing();
        }
    }

    private void clearPublishing() throws IOException {
        clear(directory, name -> name.equals(STAGING) || name.equals(REPLACED));
        Files.deleteIfExists(directory.resolve(READY));
    }

    private static void clear(Path directory, Predicate<String> include) throws IOException {
        for (Path child : children(directory, include)) {
            deleteRecursively(child);
        }
    }

    private static void copyContents(Path source, Path target, Predicate<String> include) throws IOException {
        for (Path child : children(source, include)) {
            copyRecursively(child, target.resolve(child.getFileName()));
        }
    }

    private static void moveContents(Path source, Path target, Predicate<String> include) throws IOException {
        for (Path child : children(source, include)) {
            Files.move(child, target.resolve(child.getFileName()), StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static List<Path> children(Path directory, Predicate<String> include) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.filter(child -> include.test(child.getFileName().toString()))
                    .sorted(Comparator.comparing((Path child) -> child.getFileName().toString().equals(LEVEL)))
                    .toList();
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Files.copy(path, target.resolve(source.relativize(path)), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(child);
            }
        }
    }
}
