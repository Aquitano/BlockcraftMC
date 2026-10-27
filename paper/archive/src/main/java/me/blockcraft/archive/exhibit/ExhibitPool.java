package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.TemplateDimensions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The exhibits, kept inside the archive's own map bundle so they travel with it: one directory per exhibit, named by
 * its key, holding its {@code exhibit.yml} and the dimension folder of its world. Only an exhibit with both is on
 * show. They are read on every call, so an exhibit copied in or changed by a setup command shows at once.
 */
public final class ExhibitPool {

    private static final String CONFIG = "exhibit.yml";
    private static final String WORLD = "world";

    private final Path directory;
    private final Logger logger;

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
        return exhibits().stream().filter(exhibit -> exhibit.key().equals(key)).findFirst();
    }

    public Path world(String key) {
        return directory.resolve(key).resolve(WORLD);
    }

    public void save(Exhibit exhibit) {
        exhibit.save(directory.resolve(exhibit.key()).resolve(CONFIG));
    }

    private Optional<Exhibit> read(Path folder) {
        String key = folder.getFileName().toString();
        if (!TemplateDimensions.isValidName(key)) {
            logger.warning("Skipping exhibit " + key + ": a key may only hold a-z, 0-9, _, . and -");
            return Optional.empty();
        }
        try {
            return Optional.of(Exhibit.load(key, folder.resolve(CONFIG)));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Skipping exhibit " + key + ": " + Objects.requireNonNullElse(e.getMessage(), e.toString()));
            return Optional.empty();
        }
    }
}
