package me.blockcraft.bedwars.game;

import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.bedwars.map.Border;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.shop.Dealers;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The map a round is played on and what runs on it: the resource drops, the dealers at its shops, and the wall of
 * smoke a player sees when they come close to where building ends.
 */
final class Match {

    private static final int SPARKLE_PER_STRAND = 25;
    private static final double SPARKLE_RADIUS = 1.0;
    private static final long BORDER_TICKS = 7L;
    private static final double BORDER_VISIBLE = 10.0;
    private static final double WALL_HALF_WIDTH = 6.5;
    private static final double WALL_HALF_HEIGHT = 5.0;
    private static final double WALL_STEP = 1.0;

    private final Plugin plugin;
    private final World world;
    private final ArenaMap map;
    private final Border border;
    private final Messages messages;
    private final BedWarsConfig config;
    private final Dealers dealers;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final List<Entity> shops = new ArrayList<>();

    Match(Plugin plugin, World world, ArenaMap map, Messages messages, BedWarsConfig config, Dealers dealers) {
        this.plugin = plugin;
        this.world = world;
        this.map = map;
        this.border = map.border().orElseThrow();
        this.messages = messages;
        this.config = config;
        this.dealers = dealers;
    }

    World world() {
        return world;
    }

    ArenaMap map() {
        return map;
    }

    Border border() {
        return border;
    }

    Location spectatorSpawn() {
        return map.spectator().orElseThrow().in(world);
    }

    /**
     * The chunks of every spawner and shop stay loaded: a drop lands only in a loaded chunk, and a dealer is gone
     * with its own.
     */
    void start() {
        for (var resource : Resource.values()) {
            var spots = map.spawners(resource).stream().map(point -> point.in(world)).toList();
            spots.forEach(spot -> spot.getChunk().addPluginChunkTicket(plugin));
            if (!spots.isEmpty()) {
                long interval = config.spawnTicks(resource);
                tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> drop(resource, spots), interval * 2, interval));
            }
        }
        map.shops().forEach(point -> {
            var spot = point.in(world);
            spot.getChunk().addPluginChunkTicket(plugin);
            shops.add(dealers.spawn(spot));
        });
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::showBorder, BORDER_TICKS, BORDER_TICKS));
    }

    void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        shops.forEach(dealers::remove);
        shops.clear();
    }

    private void drop(Resource resource, List<Location> spots) {
        var item = resource.item(messages);
        for (var spot : spots) {
            world.dropItem(spot, item.clone()).setVelocity(new Vector());
            resource.sparkle().ifPresent(color -> Spiral.draw(spot, Particle.ENTITY_EFFECT, color, SPARKLE_PER_STRAND, SPARKLE_RADIUS));
        }
    }

    private void showBorder() {
        for (var player : world.getPlayers()) {
            var at = player.getLocation();
            double y = at.getBlockY() + 0.5;
            if (Math.abs(at.getX() - border.minX()) < BORDER_VISIBLE) {
                wall(player, border.minX(), y, at.getBlockZ() + 0.5, true);
            }
            if (Math.abs(at.getX() - (border.maxX() + 1)) < BORDER_VISIBLE) {
                wall(player, border.maxX() + 1, y, at.getBlockZ() + 0.5, true);
            }
            if (Math.abs(at.getZ() - border.minZ()) < BORDER_VISIBLE) {
                wall(player, at.getBlockX() + 0.5, y, border.minZ(), false);
            }
            if (Math.abs(at.getZ() - (border.maxZ() + 1)) < BORDER_VISIBLE) {
                wall(player, at.getBlockX() + 0.5, y, border.maxZ() + 1, false);
            }
        }
    }

    /** A wall across x when {@code acrossX}, otherwise across z, seen by {@code player} alone. */
    private static void wall(Player player, double x, double y, double z, boolean acrossX) {
        for (double up = -WALL_HALF_HEIGHT; up < WALL_HALF_HEIGHT; up += WALL_STEP) {
            for (double side = -WALL_HALF_WIDTH; side < WALL_HALF_WIDTH; side += WALL_STEP) {
                player.spawnParticle(Particle.SMOKE, acrossX ? x : x + side, y + up, acrossX ? z + side : z, 1, 0, 0, 0, 0);
            }
        }
    }
}
