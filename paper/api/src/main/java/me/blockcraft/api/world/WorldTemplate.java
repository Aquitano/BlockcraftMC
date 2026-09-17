package me.blockcraft.api.world;

import me.blockcraft.common.config.YamlFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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
        replaceContents(directory, world, name -> true);
    }

    public void publish(Path world) {
        replaceContents(world, directory, name -> !TRANSIENT.contains(name));
    }

    private static void replaceContents(Path source, Path target, Predicate<String> include) {
        try {
            Files.createDirectories(target);
            try (Stream<Path> children = Files.list(target)) {
                for (Path child : children.toList()) {
                    deleteRecursively(child);
                }
            }
            try (Stream<Path> children = Files.list(source)) {
                for (Path child : children.filter(child -> include.test(child.getFileName().toString())).toList()) {
                    copyRecursively(child, target.resolve(child.getFileName()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + source + " to " + target, e);
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
