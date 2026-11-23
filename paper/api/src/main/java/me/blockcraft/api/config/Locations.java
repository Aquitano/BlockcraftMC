package me.blockcraft.api.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
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

    /**
     * The places as they lie in {@code world}, whichever world they were set in: for a round that plays a fresh copy
     * of the map they were set on. A place whose world is not loaded counts too, since only its coordinates matter.
     */
    public Function<K, Optional<Location>> in(World world) {
        return key -> switch (yaml.get(path.apply(key))) {
            case Location location -> {
                var moved = location.clone();
                moved.setWorld(world);
                yield Optional.of(moved);
            }
            case ConfigurationSection section -> Optional.of(new Location(world, section.getDouble("x"), section.getDouble("y"),
                    section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch")));
            case null, default -> Optional.empty();
        };
    }

    public void set(K key, Location location) {
        yaml.set(path.apply(key), location);
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
