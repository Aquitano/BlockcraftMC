package me.blockcraft.ttt.arena;

import me.blockcraft.api.world.Point;
import me.blockcraft.ttt.task.game.ChoreGames;
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
 * spectators watch, the square the world border keeps everyone in, the parts of its tester, its traitor doors and,
 * for TTT 2, its chores. The map's chests and ender chests need no entry, because every one of them takes part, and
 * neither do the wooden doors and lights a sabotage reaches, which are every one inside the border.
 */
public record TownMap(String key, String name, List<String> builders, Material icon, List<Point> spawns,
                      Point spectator, Border border, TesterParts tester, List<Spot> traitorDoors, Optional<TaskParts> tasks) {

    public TownMap {
        builders = List.copyOf(builders);
        spawns = List.copyOf(spawns);
        traitorDoors = List.copyOf(traitorDoors);
    }

    /** A square of {@code size} blocks round {@code x}/{@code z}. */
    public record Border(double x, double z, double size) {
        private static final double MAX_CENTER = 29_999_984;

        public Border {
            if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(size)
                    || Math.abs(x) > MAX_CENTER || Math.abs(z) > MAX_CENTER || size < 1 || size > MAX_CENTER * 2) {
                throw new IllegalArgumentException("border is outside Minecraft's limits");
            }
        }
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

    /** TTT 2's chores on this map and the {@code generator} where a blackout is mended. */
    public record TaskParts(Spot generator, List<ChoreParts> chores) {

        public TaskParts {
            chores = List.copyOf(chores);
            if (chores.isEmpty()) {
                throw new IllegalArgumentException("no chores");
            }
        }
    }

    /** A {@link Chore} by the spots of its blocks; a puzzle names its {@code game} because several maps may share one. */
    public sealed interface ChoreParts {

        Chore in(World world);

        record Hold(String key, Spot station, boolean visible) implements ChoreParts {
            @Override
            public Chore in(World world) {
                return new Chore.Hold(key, station.in(world), visible);
            }
        }

        record Fetch(String key, Spot station, Spot destination, Material cargo, boolean visible) implements ChoreParts {
            @Override
            public Chore in(World world) {
                return new Chore.Fetch(key, station.in(world), destination.in(world), cargo, visible);
            }
        }

        record Puzzle(String key, String game, Spot station, boolean visible) implements ChoreParts {
            @Override
            public Chore in(World world) {
                return new Chore.Puzzle(key, game, station.in(world), visible);
            }
        }

        private static ChoreParts parse(Map<?, ?> values) {
            if (!(values.get("key") instanceof String key)) {
                throw new IllegalArgumentException("a chore without a key: " + values);
            }
            boolean visible = Boolean.TRUE.equals(values.get("visible"));
            if (values.get("hold") instanceof Map<?, ?> station) {
                return new Hold(key, Spot.parse(station), visible);
            }
            if (values.get("puzzle") instanceof Map<?, ?> station) {
                var game = values.get("game") instanceof String named ? named : key;
                if (!ChoreGames.NAMES.contains(game)) {
                    throw new IllegalArgumentException("chore " + key + " plays no known game: " + game);
                }
                return new Puzzle(key, game, Spot.parse(station), visible);
            }
            if (values.get("fetch") instanceof Map<?, ?> station && values.get("to") instanceof Map<?, ?> destination) {
                var cargo = Material.matchMaterial(String.valueOf(values.get("cargo")));
                if (cargo == null) {
                    throw new IllegalArgumentException("chore " + key + " carries no item: " + values.get("cargo"));
                }
                return new Fetch(key, Spot.parse(station), Spot.parse(destination), cargo, visible);
            }
            throw new IllegalArgumentException("chore " + key + " is neither hold, puzzle nor fetch with a to");
        }
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
        var tasks = Optional.ofNullable(yaml.getConfigurationSection("tasks"))
                .map(section -> new TaskParts(Spot.parse(section(section, "generator")),
                        section.getMapList("chores").stream().map(ChoreParts::parse).toList()));
        return new TownMap(key, yaml.getString("name", key), yaml.getStringList("builders"),
                icon == null ? Material.PAPER : icon, spawns, Point.parse(section(yaml, "spectator")),
                new Border(Point.number(border, "x"), Point.number(border, "z"), Point.number(border, "size")),
                new TesterParts(Box.parse(section(yaml, "tester.zone")), Spot.parse(section(yaml, "tester.button")),
                        Box.parse(section(yaml, "tester.entrance")), lamps, trap),
                yaml.getMapList("traitor-doors").stream().map(Spot::parse).toList(), tasks);
    }

    /**
     * The round's town in {@code world}, this map's loaded copy, with the world border drawn. Its chores come along
     * only {@code withTasks}, because finding the doors and lights a sabotage reaches reads every block of the map.
     */
    public Town in(World world, boolean withTasks) {
        var worldBorder = world.getWorldBorder();
        worldBorder.setCenter(border.x(), border.z());
        worldBorder.setSize(border.size());
        var room = new TesterRoom(tester.zone(), tester.button().in(world), tester.entrance().blocks(world),
                tester.lamps().stream().map(lamp -> lamp.in(world)).toList(),
                tester.trap().map(trap -> new TesterRoom.Trap(trap.button().in(world), trap.floor().blocks(world))));
        var layout = tasks.filter(parts -> withTasks).map(parts -> {
            var fittings = Fittings.inside(world, border);
            return new TaskLayout(parts.chores().stream().map(chore -> chore.in(world)).toList(), fittings.doors(),
                    fittings.lights(), parts.generator().in(world));
        });
        return new Town(name, world, spawns.stream().map(point -> point.in(world)).toList(), room, spectator.in(world),
                traitorDoors.stream().map(door -> door.in(world)).toList(), layout);
    }

    private static Map<String, Object> section(ConfigurationSection parent, String path) {
        var section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("no " + path);
        }
        return section.getValues(false);
    }
}
