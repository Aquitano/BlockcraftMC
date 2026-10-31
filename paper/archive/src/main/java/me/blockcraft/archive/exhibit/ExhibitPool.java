package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.TemplateDimensions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The exhibits, kept in their own template directory beside the archive's map bundle, so that neither a start nor
 * {@code /map save} copies them: one directory per exhibit, named by its key, holding its {@code exhibit.yml} and the
 * dimension folder of its world. Only an exhibit with both is on show. File metadata is checked on every call;
 * unchanged exhibits reuse their parsed configuration, while imports and setup edits show at once.
 */
public final class ExhibitPool {

    private static final String CONFIG = "exhibit.yml";
    private static final String WORLD = "world";

    private record Cached(BasicFileAttributes attributes, Exhibit exhibit) {
        boolean matches(BasicFileAttributes current) {
            return attributes.size() == current.size()
                    && attributes.lastModifiedTime().equals(current.lastModifiedTime())
                    && Objects.equals(attributes.fileKey(), current.fileKey());
        }
    }

    private final Path directory;
    private final Logger logger;
    private final Map<String, Cached> cached = new HashMap<>();

    public ExhibitPool(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public List<Exhibit> exhibits() {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> folders = Files.list(directory)) {
            return folders.filter(folder -> Files.isRegularFile(folder.resolve(CONFIG)) && Files.isDirectory(folder.resolve(WORLD)))
                    .map(this::read)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(Exhibit::key))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the exhibits in " + directory, e);
        }
    }

    public Optional<Exhibit> exhibit(String key) {
        if (!TemplateDimensions.isValidName(key)) {
            return Optional.empty();
        }
        var folder = directory.resolve(key);
        if (!Files.isRegularFile(folder.resolve(CONFIG)) || !Files.isDirectory(folder.resolve(WORLD))) {
            cached.remove(key);
            return Optional.empty();
        }
        return read(folder);
    }

    public Path world(String key) {
        return directory.resolve(key).resolve(WORLD);
    }

    public void save(Exhibit exhibit) {
        exhibit.save(directory.resolve(exhibit.key()).resolve(CONFIG));
        cached.remove(exhibit.key());
    }

    private Optional<Exhibit> read(Path folder) {
        String key = folder.getFileName().toString();
        if (!TemplateDimensions.isValidName(key)) {
            logger.warning("Skipping exhibit " + key + ": a key may only hold a-z, 0-9, _, . and -");
            return Optional.empty();
        }
        try {
            var file = folder.resolve(CONFIG);
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            var previous = cached.get(key);
            if (previous != null && previous.matches(attributes)) {
                return Optional.of(previous.exhibit());
            }
            var exhibit = Exhibit.load(key, file);
            cached.put(key, new Cached(attributes, exhibit));
            return Optional.of(exhibit);
        } catch (IOException | RuntimeException e) {
            cached.remove(key);
            logger.log(Level.WARNING, "Skipping exhibit " + key + ": " + Objects.requireNonNullElse(e.getMessage(), e.toString()));
            return Optional.empty();
        }
    }
}
