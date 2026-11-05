package me.blockcraft.sternenfront.arena;

import me.blockcraft.sternenfront.arena.FrontMap.Control;
import me.blockcraft.sternenfront.arena.FrontMap.ControlSite;
import me.blockcraft.sternenfront.arena.FrontMap.Headquarters;
import me.blockcraft.sternenfront.arena.FrontMap.PostSite;
import me.blockcraft.sternenfront.arena.FrontMap.Route;
import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A map's blocks are saved in its world template, and this reads every position from the {@code arena.yml} that travels
 * with them. Positions are {@code x y z} strings with an optional yaw and pitch, boxes are min and max corners. A map may
 * leave out {@code secrets}, and may set {@code weather: rain} and a fixed {@code time} of day.
 */
public final class ArenaFile {

    public static final String FILE = "arena.yml";

    private ArenaFile() { }

    public static FrontMap load(World world, Path file) {
        if (Files.notExists(file)) {
            throw new IllegalStateException("The map has no " + file + "; set BLOCKCRAFT_WORLD_TEMPLATE to a map's template");
        }
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var headquarters = new EnumMap<Faction, Headquarters>(Faction.class);
        for (var key : yaml.getConfigurationSection("headquarters").getKeys(false)) {
            var section = yaml.getConfigurationSection("headquarters." + key);
            headquarters.put(Faction.valueOf(key.toUpperCase(Locale.ROOT)),
                    new Headquarters(spots(world, section.getStringList("spawns")), box(section.getDoubleList("zone"))));
        }
        var posts = yaml.getMapList("posts").stream().map(post -> post(world, post)).toList();
        var controls = yaml.getMapList("controls").stream().map(control -> control(world, control)).toList();
        var playArea = box(yaml.getDoubleList("play-area"));
        double minimumBorder = Math.max(playArea.getWidthX(), playArea.getWidthZ()) + 2;
        return new FrontMap(yaml.getString("name"), world, playArea, Map.copyOf(headquarters), posts,
                spot(world, yaml.getString("relic")), spot(world, yaml.getString("overview")), spot(world, yaml.getString("lobby")),
                spot(world, yaml.getString("spectator")), Optional.ofNullable(yaml.getConfigurationSection("secrets")).map(secrets -> secrets(world, secrets)),
                controls, "rain".equals(yaml.getString("weather")), yaml.contains("time") ? OptionalLong.of(yaml.getLong("time")) : OptionalLong.empty(),
                environment(world, yaml.getConfigurationSection("environment")),
                Math.max(minimumBorder, yaml.getDouble("world-border-size", minimumBorder)),
                yaml.getDouble("death-y", playArea.getMinY() - 10));
    }

    private static YardEnvironment environment(World world, ConfigurationSection environment) {
        if (environment == null) return YardEnvironment.EMPTY;
        return new YardEnvironment(spots(world, environment.getStringList("steam")), spots(world, environment.getStringList("sparks")),
                Optional.ofNullable(environment.getConfigurationSection("crane"))
                        .map(crane -> new YardEnvironment.Crane(spot(world, crane.getString("from")), spot(world, crane.getString("to")))));
    }

    private static SecretSpots secrets(World world, ConfigurationSection secrets) {
        return new SecretSpots(spots(world, secrets.getStringList("crystals")), spots(world, secrets.getStringList("droid-path")),
                Optional.ofNullable(secrets.getConfigurationSection("dune"))
                        .map(dune -> new SecretSpots.Dune(spot(world, dune.getString("top")), dune.getDouble("radius"))),
                Optional.ofNullable(secrets.getString("music-box")).map(at -> spot(world, at)),
                Optional.ofNullable(secrets.getConfigurationSection("course")).map(course -> course(world, course)),
                Optional.ofNullable(secrets.getString("receiver")).map(at -> spot(world, at)),
                Optional.ofNullable(secrets.getConfigurationSection("inspection-course")).map(course -> course(world, course)));
    }

    private static SecretSpots.Course course(World world, ConfigurationSection course) {
        return new SecretSpots.Course(box(course.getDoubleList("lane")), course.getDouble("pit-y"),
                course.getDoubleList("gates"), spot(world, course.getString("board")));
    }

    private static PostSite post(World world, Map<?, ?> post) {
        var routes = new EnumMap<Faction, List<Route>>(Faction.class);
        ((Map<?, ?>) post.get("routes")).forEach((faction, list) -> routes.put(Faction.valueOf(String.valueOf(faction).toUpperCase(Locale.ROOT)),
                ((List<?>) list).stream().map(route -> route(world, (Map<?, ?>) route)).toList()));
        return new PostSite(String.valueOf(post.get("key")), spot(world, post.get("center")), ((Number) post.get("radius")).doubleValue(),
                spots(world, (List<?>) post.get("spawns")), blocks(world, (List<?>) post.get("indicators")), spot(world, post.get("label")),
                Map.copyOf(routes));
    }

    private static Route route(World world, Map<?, ?> route) {
        return new Route(spots(world, (List<?>) route.get("approach")), spot(world, route.get("cover")), spot(world, route.get("peek")),
                spot(world, route.get("retreat")), spot(world, route.get("capture")));
    }

    private static ControlSite control(World world, Map<?, ?> control) {
        return new ControlSite(Control.valueOf(String.valueOf(control.get("control")).toUpperCase(Locale.ROOT)),
                blocks(world, (List<?>) control.get("switches")), blocks(world, (List<?>) control.get("panels")), spot(world, control.get("center")));
    }

    private static List<Location> spots(World world, List<?> spots) {
        return spots.stream().map(spot -> spot(world, spot)).toList();
    }

    private static Location spot(World world, Object spot) {
        var parts = String.valueOf(spot).split(" ");
        var at = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        if (parts.length == 5) {
            at.setYaw(Float.parseFloat(parts[3]));
            at.setPitch(Float.parseFloat(parts[4]));
        }
        return at;
    }

    private static List<Block> blocks(World world, List<?> blocks) {
        return new ArrayList<>(blocks.stream().map(block -> block(world, block)).toList());
    }

    private static Block block(World world, Object block) {
        var parts = String.valueOf(block).split(" ");
        return world.getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static BoundingBox box(List<Double> box) {
        return new BoundingBox(box.get(0), box.get(1), box.get(2), box.get(3), box.get(4), box.get(5));
    }
}
