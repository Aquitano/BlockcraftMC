package me.blockcraft.api.config;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Keyed {@link Location}s persisted in a YAML file through Bukkit's own Location serialisation. */
public final class Locations<K> {

    private final Path file;
    private final YamlConfiguration yaml;
    private final Function<K, String> path;

    private Locations(Path file, Function<K, String> path) {
        this.file = file;
        this.yaml = YamlConfiguration.loadConfiguration(file.toFile());
        this.path = path;
    }

    public static <K> Locations<K> load(Path file, Function<K, String> path) {
        return new Locations<>(file, path);
    }

    public static Locations<String> load(Path file) {
        return load(file, Function.identity());
    }

    /** Empty when the key is unset or its world is not loaded. */
    public Optional<Location> get(K key) {
        return Optional.ofNullable(yaml.getLocation(path.apply(key))).filter(Location::isWorldLoaded);
    }

    public void set(K key, Location location) {
        yaml.set(path.apply(key), location);
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }

    /** Every stored location whose world is loaded. */
    public List<Location> all() {
        return yaml.getKeys(true).stream()
                .map(yaml::getLocation)
                .filter(location -> location != null && location.isWorldLoaded())
                .toList();
    }
}
