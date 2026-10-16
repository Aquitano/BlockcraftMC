package me.blockcraft.api.world;

import me.blockcraft.common.config.YamlFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A map kept outside the server. The world folder is replaced by a copy of the template at every start, so
 * nothing a round or a builder left behind survives, and {@link #publish} copies the running world back once a
 * change is meant to stay. Player state and the session lock never travel with a map. Both directions replace the
 * contents of a directory rather than the directory, because either side may be a mount point.
 */
public final class WorldTemplate {

    private static final String ENV = "BLOCKCRAFT_WORLD_TEMPLATE";
    private static final Set<String> TRANSIENT = Set.of("session.lock", "players", "playerdata", "stats", "advancements", "level.dat_old");
    private static final String STAGING = ".staging";
    private static final String REPLACED = ".replaced";
    private static final Set<String> PUBLISHING = Set.of(STAGING, REPLACED);

    private final Path directory;

    private WorldTemplate(Path directory) {
        this.directory = directory;
    }

    public static WorldTemplate at(Path directory) {
        return new WorldTemplate(directory.toAbsolutePath().normalize());
    }

    /** {@code BLOCKCRAFT_WORLD_TEMPLATE}, else {@code world.template} of the config; empty when neither names a directory. */
    public static Optional<WorldTemplate> configured(Map<String, Object> config) {
        String env = System.getenv(ENV);
        String path = env != null && !env.isBlank() ? env : String.valueOf(YamlFile.section(config, "world").getOrDefault("template", ""));
        return path.isBlank() ? Optional.empty() : Optional.of(at(Path.of(path)));
    }

    public Path directory() {
        return directory;
    }

    /** A map is a directory with a level.dat; an empty or missing directory is a template nobody has published to yet. */
    public boolean exists() {
        return Files.isRegularFile(directory.resolve("level.dat"));
    }

    public void restore(Path world) {
        try {
            Files.createDirectories(world);
            clear(world, name -> true);
            copyContents(directory, world, name -> !PUBLISHING.contains(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + directory + " to " + world, e);
        }
    }

    /**
     * Copies the world into a staging directory inside the template and only then swaps the contents with renames,
     * which stay on the template's file system, so a copy that fails or is cut short leaves the old map in place.
     * The leftovers of such a copy are cleared by the next publish and never restored.
     */
    public void publish(Path world) {
        Path staging = directory.resolve(STAGING);
        Path replaced = directory.resolve(REPLACED);
        try {
            Files.createDirectories(directory);
            clear(directory, PUBLISHING::contains);
            Files.createDirectories(staging);
            copyContents(world, staging, name -> !TRANSIENT.contains(name));
            Files.createDirectories(replaced);
            moveContents(directory, replaced, name -> !PUBLISHING.contains(name));
            moveContents(staging, directory, name -> true);
            clear(directory, PUBLISHING::contains);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + world + " to " + directory, e);
        }
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
            return children.filter(child -> include.test(child.getFileName().toString())).toList();
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
