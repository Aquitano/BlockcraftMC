package me.blockcraft.ttt.arena;

import me.blockcraft.api.world.Point;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An imported map of the pool, as its {@code map.yml} describes it: what the vote shows, where players start and
 * spectators watch, the square the world border keeps everyone in, and the parts of its tester. The map's chests
 * and ender chests need no entry, because every one of them takes part.
 */
public record TownMap(String key, String name, List<String> builders, Material icon, List<Point> spawns,
                      Point spectator, Border border, TesterParts tester) {

    public TownMap {
        builders = List.copyOf(builders);
        spawns = List.copyOf(spawns);
    }

    /** A square of {@code size} blocks round {@code x}/{@code z}. */
    public record Border(double x, double z, double size) {
    }

    /**
     * Whoever stands in {@code zone} is tested; {@code entrance} shuts and the {@code lamps} show the result. A
     * {@code trap} drops the room's floor into a pit.
     */
    public record TesterParts(Box zone, Spot button, Box entrance, List<Spot> lamps, Optional<TrapParts> trap) {

        public TesterParts {
            lamps = List.copyOf(lamps);
        }
    }

    /** The hidden {@code button} that opens the tester's {@code floor}. */
    public record TrapParts(Spot button, Box floor) {
    }

    public record Spot(int x, int y, int z) {

        static Spot parse(Map<?, ?> values) {
            return new Spot((int) Point.number(values, "x"), (int) Point.number(values, "y"), (int) Point.number(values, "z"));
        }

        Block in(World world) {
            return world.getBlockAt(x, y, z);
        }
    }

    /** Throws for a file that leaves out anything a round needs. */
    public static TownMap load(String key, Path file) {
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var spawns = yaml.getMapList("spawns").stream().map(Point::parse).toList();
        if (spawns.isEmpty()) {
            throw new IllegalArgumentException("no spawns");
        }
        var border = section(yaml, "border");
        var lamps = yaml.getMapList("tester.lamps").stream().map(Spot::parse).toList();
        if (lamps.isEmpty()) {
            throw new IllegalArgumentException("no tester lamps");
        }
        var trap = Optional.ofNullable(yaml.getConfigurationSection("tester.trap"))
                .map(section -> new TrapParts(Spot.parse(section(section, "button")), Box.parse(section(section, "floor"))));
        var icon = Material.matchMaterial(yaml.getString("icon", "paper"));
        return new TownMap(key, yaml.getString("name", key), yaml.getStringList("builders"),
                icon == null ? Material.PAPER : icon, spawns, Point.parse(section(yaml, "spectator")),
                new Border(Point.number(border, "x"), Point.number(border, "z"), Point.number(border, "size")),
                new TesterParts(Box.parse(section(yaml, "tester.zone")), Spot.parse(section(yaml, "tester.button")),
                        Box.parse(section(yaml, "tester.entrance")), lamps, trap));
    }

    /** The round's town in {@code world}, this map's loaded copy, with the world border drawn. */
    public Town in(World world) {
        var worldBorder = world.getWorldBorder();
        worldBorder.setCenter(border.x(), border.z());
        worldBorder.setSize(border.size());
        var room = new TesterRoom(tester.zone(), tester.button().in(world), tester.entrance().blocks(world),
                tester.lamps().stream().map(lamp -> lamp.in(world)).toList(),
                tester.trap().map(trap -> new TesterRoom.Trap(trap.button().in(world), trap.floor().blocks(world))));
        return new Town(name, world, spawns.stream().map(point -> point.in(world)).toList(), room, spectator.in(world));
    }

    private static Map<String, Object> section(ConfigurationSection parent, String path) {
        var section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("no " + path);
        }
        return section.getValues(false);
    }
}
