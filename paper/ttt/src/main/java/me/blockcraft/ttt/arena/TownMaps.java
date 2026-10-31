package me.blockcraft.ttt.arena;

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
 * The imported maps a round can be played on, kept inside the lobby's own map bundle so they travel with it: one
 * directory per map, named by its key, holding its {@code map.yml} and the dimension folder of its world. A map
 * whose file a round could not play from stays out, named in the log.
 */
public final class TownMaps {

    private static final String CONFIG = "map.yml";
    private static final String WORLD = "world";

    private final Path directory;
    private final Logger logger;

    public TownMaps(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public List<TownMap> maps() {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> folders = Files.list(directory)) {
            return folders.filter(folder -> Files.isRegularFile(folder.resolve(CONFIG)) && Files.isDirectory(folder.resolve(WORLD)))
                    .map(this::read)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(TownMap::key))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the maps in " + directory, e);
        }
    }

    public Path world(String key) {
        return directory.resolve(key).resolve(WORLD);
    }

    private Optional<TownMap> read(Path folder) {
        String key = folder.getFileName().toString();
        if (!TemplateDimensions.isValidName(key)) {
            logger.warning("Skipping map " + key + ": a key may only hold a-z, 0-9, _, . and -");
            return Optional.empty();
        }
        try {
            return Optional.of(TownMap.load(key, folder.resolve(CONFIG)));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Skipping map " + key + ": " + Objects.requireNonNullElse(e.getMessage(), e.toString()));
            return Optional.empty();
        }
    }
}
