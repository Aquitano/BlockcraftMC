package me.blockcraft.bedwars.map;

import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.bedwars.team.TeamColor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The maps a round can be played on, kept inside the lobby's own map bundle so they travel with it: one directory
 * per map, named by its key, holding its {@code map.yml} and the dimension folder of its world. They are read on
 * every call, so a map a builder just saved is in the next vote.
 */
public final class MapPool {

    private static final String CONFIG = "map.yml";
    private static final String WORLD = "world";

    private final Path directory;
    private final Logger logger;

    public MapPool(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public List<ArenaMap> maps() {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> folders = Files.list(directory)) {
            return folders.filter(folder -> Files.isRegularFile(folder.resolve(CONFIG)))
                    .map(this::read)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(ArenaMap::key))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the maps in " + directory, e);
        }
    }

    /** The maps whose world is there and which seat every one of {@code teams}. */
    public List<ArenaMap> playable(Collection<TeamColor> teams) {
        return maps().stream()
                .filter(map -> map.missing(teams).isEmpty() && Files.isDirectory(world(map.key())))
                .toList();
    }

    public Optional<ArenaMap> map(String key) {
        return maps().stream().filter(map -> map.key().equals(key)).findFirst();
    }

    public Path world(String key) {
        return directory.resolve(key).resolve(WORLD);
    }

    public void save(ArenaMap map) {
        var file = directory.resolve(map.key()).resolve(CONFIG);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + file.getParent(), e);
        }
        map.save(file);
    }

    private Optional<ArenaMap> read(Path folder) {
        String key = folder.getFileName().toString();
        if (!TemplateDimensions.isValidName(key)) {
            logger.warning("Skipping map " + key + ": a key may only hold a-z, 0-9, _, . and -");
            return Optional.empty();
        }
        try {
            return Optional.of(ArenaMap.load(key, folder.resolve(CONFIG)));
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Skipping map " + key + ": " + Objects.requireNonNullElse(e.getMessage(), e.toString()));
            return Optional.empty();
        }
    }
}
