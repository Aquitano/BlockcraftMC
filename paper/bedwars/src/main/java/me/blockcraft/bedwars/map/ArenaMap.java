package me.blockcraft.bedwars.map;

import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.team.TeamColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One map of the pool, as its {@code map.yml} describes it: what the vote shows, where players may build, where
 * spectators, shops, spawners, team spawns and beds are. A map that misses something the configured teams need
 * stays out of the vote; {@link #missing} names what, for the builder.
 */
public record ArenaMap(String key, String name, List<String> builders, Material icon, Optional<Border> border,
                       Optional<Point> spectator, List<Point> shops, Map<Resource, List<Point>> spawners,
                       Map<TeamColor, Point> spawns, Map<TeamColor, BedSpot> beds) {

    public ArenaMap {
        builders = List.copyOf(builders);
        shops = List.copyOf(shops);
        spawners = Collections.unmodifiableMap(copy(Resource.class, spawners));
        spawns = Collections.unmodifiableMap(copy(TeamColor.class, spawns));
        beds = Collections.unmodifiableMap(copy(TeamColor.class, beds));
    }

    public static ArenaMap empty(String key) {
        return new ArenaMap(key, key, List.of(), Material.PAPER, Optional.empty(), Optional.empty(), List.of(),
                Map.of(), Map.of(), Map.of());
    }

    public static ArenaMap load(String key, Path file) {
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var spawners = new EnumMap<Resource, List<Point>>(Resource.class);
        for (var resource : Resource.values()) {
            spawners.put(resource, points(yaml, "spawners." + resource.key()));
        }
        var spawns = new EnumMap<TeamColor, Point>(TeamColor.class);
        var beds = new EnumMap<TeamColor, BedSpot>(TeamColor.class);
        var teams = yaml.getConfigurationSection("teams");
        if (teams != null) {
            for (var teamKey : teams.getKeys(false)) {
                var color = TeamColor.byKey(teamKey).orElseThrow(() -> new IllegalArgumentException("unknown team " + teamKey));
                section(teams, teamKey + ".spawn").map(Point::parse).ifPresent(point -> spawns.put(color, point));
                section(teams, teamKey + ".bed").map(BedSpot::parse).ifPresent(bed -> beds.put(color, bed));
            }
        }
        var icon = Material.matchMaterial(yaml.getString("icon", "paper"));
        return new ArenaMap(key, yaml.getString("name", key), yaml.getStringList("builders"),
                icon == null || !icon.isItem() ? Material.PAPER : icon,
                section(yaml, "border").map(Border::parse), section(yaml, "spectator").map(Point::parse),
                points(yaml, "shops"), spawners, spawns, beds);
    }

    public void save(Path file) {
        var yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("builders", builders);
        yaml.set("icon", icon.getKey().getKey());
        border.ifPresent(value -> yaml.set("border", value.serialize()));
        spectator.ifPresent(value -> yaml.set("spectator", value.serialize()));
        yaml.set("shops", shops.stream().map(Point::serialize).toList());
        spawners.forEach((resource, points) -> yaml.set("spawners." + resource.key(), points.stream().map(Point::serialize).toList()));
        for (var color : TeamColor.values()) {
            Optional.ofNullable(spawns.get(color)).ifPresent(point -> yaml.set("teams." + color.key() + ".spawn", point.serialize()));
            Optional.ofNullable(beds.get(color)).ifPresent(bed -> yaml.set("teams." + color.key() + ".bed", bed.serialize()));
        }
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }

    public List<Point> spawners(Resource resource) {
        return spawners.getOrDefault(resource, List.of());
    }

    /** What the map still needs before {@code teams} can play on it; empty when it is ready for the vote. */
    public List<String> missing(Collection<TeamColor> teams) {
        var missing = new ArrayList<String>();
        if (border.isEmpty()) {
            missing.add("border");
        }
        if (spectator.isEmpty()) {
            missing.add("spectator");
        }
        for (var color : teams) {
            if (!spawns.containsKey(color)) {
                missing.add("spawn " + color.key());
            }
            if (!beds.containsKey(color)) {
                missing.add("bed " + color.key());
            }
        }
        return missing;
    }

    public ArenaMap withName(String value) {
        return new ArenaMap(key, value, builders, icon, border, spectator, shops, spawners, spawns, beds);
    }

    public ArenaMap withIcon(Material value) {
        return new ArenaMap(key, name, builders, value, border, spectator, shops, spawners, spawns, beds);
    }

    public ArenaMap withBorder(Border value) {
        return new ArenaMap(key, name, builders, icon, Optional.of(value), spectator, shops, spawners, spawns, beds);
    }

    public ArenaMap withSpectator(Point value) {
        return new ArenaMap(key, name, builders, icon, border, Optional.of(value), shops, spawners, spawns, beds);
    }

    public ArenaMap withShops(List<Point> value) {
        return new ArenaMap(key, name, builders, icon, border, spectator, value, spawners, spawns, beds);
    }

    public ArenaMap withSpawners(Resource resource, List<Point> value) {
        var changed = copy(Resource.class, spawners);
        changed.put(resource, value);
        return new ArenaMap(key, name, builders, icon, border, spectator, shops, changed, spawns, beds);
    }

    public ArenaMap withSpawn(TeamColor color, Point value) {
        var changed = copy(TeamColor.class, spawns);
        changed.put(color, value);
        return new ArenaMap(key, name, builders, icon, border, spectator, shops, spawners, changed, beds);
    }

    public ArenaMap withBed(TeamColor color, BedSpot value) {
        var changed = copy(TeamColor.class, beds);
        changed.put(color, value);
        return new ArenaMap(key, name, builders, icon, border, spectator, shops, spawners, spawns, changed);
    }

    private static Optional<Map<String, Object>> section(ConfigurationSection parent, String path) {
        return Optional.ofNullable(parent.getConfigurationSection(path)).map(section -> section.getValues(false));
    }

    private static List<Point> points(ConfigurationSection parent, String path) {
        return parent.getMapList(path).stream().map(Point::parse).toList();
    }

    private static <K extends Enum<K>, V> EnumMap<K, V> copy(Class<K> type, Map<K, V> values) {
        var copy = new EnumMap<K, V>(type);
        copy.putAll(values);
        return copy;
    }
}
