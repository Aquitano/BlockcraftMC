package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.Point;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One old map on show, as its {@code exhibit.yml} describes it: what the menu calls it, which server and year it comes
 * from, where a visitor arrives and the checkpoints worth a jump. Without an arrival a visitor lands on the world's
 * own spawn.
 */
public record Exhibit(String key, String name, String origin, OptionalInt year, Optional<String> category, String description, Material icon,
                      Optional<Point> arrival, List<Checkpoint> checkpoints) {

    private static final Material DEFAULT_ICON = Material.PAPER;

    public Exhibit {
        checkpoints = List.copyOf(checkpoints);
    }

    public static Exhibit load(String key, Path file) {
        return parse(key, YamlConfiguration.loadConfiguration(file.toFile()));
    }

    static Exhibit parse(String key, ConfigurationSection yaml) {
        var origin = yaml.getString("origin", "");
        if (origin.isBlank()) {
            throw new IllegalArgumentException("missing origin");
        }
        var icon = Material.matchMaterial(yaml.getString("icon", ""));
        var checkpoints = yaml.getMapList("checkpoints").stream().map(Exhibit::checkpoint).toList();
        return new Exhibit(key, yaml.getString("name", key), origin,
                yaml.isInt("year") ? OptionalInt.of(yaml.getInt("year")) : OptionalInt.empty(),
                Optional.ofNullable(yaml.getString("category")).map(String::strip).filter(value -> !value.isEmpty()),
                yaml.getString("description", ""), icon == null ? DEFAULT_ICON : icon,
                Optional.ofNullable(yaml.getConfigurationSection("arrival")).map(section -> Point.parse(section.getValues(false))),
                checkpoints);
    }

    public void save(Path file) {
        try {
            yaml().save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }

    YamlConfiguration yaml() {
        var yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("origin", origin);
        year.ifPresent(value -> yaml.set("year", value));
        category.ifPresent(value -> yaml.set("category", value));
        yaml.set("description", description);
        yaml.set("icon", icon.getKey().getKey());
        arrival.ifPresent(point -> yaml.set("arrival", point.serialize()));
        yaml.set("checkpoints", checkpoints.stream().map(Exhibit::serialize).toList());
        return yaml;
    }

    public Optional<Checkpoint> checkpoint(String name) {
        return checkpoints.stream().filter(checkpoint -> checkpoint.name().equalsIgnoreCase(name)).findFirst();
    }

    public Exhibit withArrival(Point value) {
        return new Exhibit(key, name, origin, year, category, description, icon, Optional.of(value), checkpoints);
    }

    /** Adds the checkpoint, or puts it in the place of the one with its name. */
    public Exhibit withCheckpoint(Checkpoint value) {
        var changed = new ArrayList<Checkpoint>();
        boolean replaced = false;
        for (var checkpoint : checkpoints) {
            if (checkpoint.name().equalsIgnoreCase(value.name())) {
                changed.add(value);
                replaced = true;
            } else {
                changed.add(checkpoint);
            }
        }
        if (!replaced) {
            changed.add(value);
        }
        return new Exhibit(key, name, origin, year, category, description, icon, arrival, changed);
    }

    public Exhibit withoutCheckpoint(String name) {
        return new Exhibit(key, this.name, origin, year, category, description, icon, arrival,
                checkpoints.stream().filter(checkpoint -> !checkpoint.name().equalsIgnoreCase(name)).toList());
    }

    private static Checkpoint checkpoint(Map<?, ?> values) {
        if (!(values.get("name") instanceof String name) || !(values.get("at") instanceof Map<?, ?> at)) {
            throw new IllegalArgumentException("a checkpoint needs a name and 'at': " + values);
        }
        var description = values.get("description") instanceof String text ? text : "";
        return new Checkpoint(name, description, Point.parse(at));
    }

    private static Map<String, Object> serialize(Checkpoint checkpoint) {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", checkpoint.name());
        values.put("description", checkpoint.description());
        values.put("at", checkpoint.at().serialize());
        return values;
    }
}
