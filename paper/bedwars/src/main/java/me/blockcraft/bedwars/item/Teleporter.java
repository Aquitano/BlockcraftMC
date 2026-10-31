package me.blockcraft.bedwars.item;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Home by rocket: under open sky the player counts down a few seconds without moving off their block, then shoots
 * up and lands on the team's spawn once they are high enough. Leaving the block, losing the item or a roof above
 * cancels it.
 */
final class Teleporter {

    private static final long SECOND = 20L;
    /** The fastest a client takes a velocity from the server, in blocks per tick. */
    private static final Vector LAUNCH = new Vector(0.0, 3.9, 0.0);
    private static final long MAX_FLIGHT_TICKS = 100L;
    private static final double RING_RADIUS = 0.75;
    private static final int RING_POINTS = 20;
    private static final double RING_HEIGHT = 2.2;
    private static final double CORNER = 0.2;

    private record Flight(int launchHeight, BukkitTask task) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final int seconds;
    private final int height;
    private final Function<Player, Optional<Location>> spawnOf;
    private final Map<UUID, BukkitTask> charging = new HashMap<>();
    private final Map<UUID, Flight> flying = new HashMap<>();

    Teleporter(Plugin plugin, Messages messages, int seconds, int height, Function<Player, Optional<Location>> spawnOf) {
        this.plugin = plugin;
        this.messages = messages;
        this.seconds = seconds;
        this.height = height;
        this.spawnOf = spawnOf;
    }

    void use(Player player) {
        if (charging.containsKey(player.getUniqueId())) {
            player.sendMessage(messages.get("items.teleporter.already"));
            return;
        }
        if (roofed(player.getLocation())) {
            player.sendMessage(messages.get("items.teleporter.roof"));
            return;
        }
        int[] left = {seconds};
        charging.put(player.getUniqueId(), Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (roofed(player.getLocation())) {
                player.sendMessage(messages.get("items.teleporter.roof"));
                stopCharging(player);
            } else if (!hasTeleporter(player)) {
                player.sendMessage(messages.get("items.teleporter.lost"));
                stopCharging(player);
            } else if (left[0] == 0) {
                stopCharging(player);
                takeOne(player);
                launch(player);
            } else {
                ring(player.getLocation().add(0, RING_HEIGHT, 0));
                player.getWorld().getPlayers().forEach(Sounds.NOTE_PLING::play);
                left[0]--;
            }
        }, 0L, SECOND));
    }

    /** Leaving the block one started on stops the countdown; a launched player arrives once high enough. */
    void moved(Player player, Location from, Location to) {
        var flight = flying.get(player.getUniqueId());
        if (flight != null && to.getY() > flight.launchHeight() + height) {
            land(player);
            return;
        }
        if (charging.containsKey(player.getUniqueId()) && !from.getBlock().equals(to.getBlock())) {
            player.sendMessage(messages.get("items.teleporter.cancelled"));
            stopCharging(player);
        }
    }

    void forget(Player player) {
        stopCharging(player);
        var flight = flying.remove(player.getUniqueId());
        if (flight != null) {
            flight.task().cancel();
        }
    }

    void clear() {
        charging.values().forEach(BukkitTask::cancel);
        charging.clear();
        flying.values().forEach(flight -> flight.task().cancel());
        flying.clear();
    }

    private void launch(Player player) {
        int launchHeight = player.getLocation().getBlockY();
        long[] ticks = {0};
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || ++ticks[0] > MAX_FLIGHT_TICKS) {
                forget(player);
                return;
            }
            player.setVelocity(LAUNCH);
        }, 0L, 1L);
        flying.put(player.getUniqueId(), new Flight(launchHeight, task));
    }

    private void land(Player player) {
        forget(player);
        spawnOf.apply(player).ifPresent(player::teleport);
        player.setVelocity(new Vector());
        player.setFallDistance(0.0f);
    }

    private void stopCharging(Player player) {
        var task = charging.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private static boolean hasTeleporter(Player player) {
        for (var item : player.getInventory().getStorageContents()) {
            if (HotbarItem.of(item, SpecialItem.class).filter(SpecialItem.TELEPORTER::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static void takeOne(Player player) {
        var contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (HotbarItem.of(contents[slot], SpecialItem.class).filter(SpecialItem.TELEPORTER::equals).isPresent()) {
                contents[slot].subtract();
                player.getInventory().setItem(slot, contents[slot].isEmpty() ? null : contents[slot]);
                return;
            }
        }
    }

    /** Any block above the corners of the player's feet counts as a roof. */
    private static boolean roofed(Location location) {
        var world = location.getWorld();
        int y = location.getBlockY();
        for (double dx : new double[] {-CORNER, CORNER}) {
            for (double dz : new double[] {-CORNER, CORNER}) {
                int x = Location.locToBlock(location.getX() + dx);
                int z = Location.locToBlock(location.getZ() + dz);
                if (world.getHighestBlockYAt(x, z) > y) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void ring(Location center) {
        for (int point = 0; point < RING_POINTS; point++) {
            double angle = 2 * Math.PI * point / RING_POINTS;
            center.getWorld().spawnParticle(Particle.FIREWORK,
                    center.clone().add(Math.cos(angle) * RING_RADIUS, 0, Math.sin(angle) * RING_RADIUS), 1, 0, 0, 0, 0);
        }
    }
}
