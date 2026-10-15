package me.blockcraft.bedwars.item;

import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A chicken on the head that turns a fall into a glide where the player looks, at four times their walking speed
 * and without fall damage. It breaks after a while and is gone on landing.
 */
final class Parachute {

    private static final double SINK = 0.8;
    private static final double GLIDE = 4.0;
    private static final double GROUND_PROBE = 0.05;

    private record Canopy(Chicken chicken, BukkitTask expiry) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final int seconds;
    private final Map<UUID, Canopy> open = new HashMap<>();

    Parachute(Plugin plugin, Messages messages, int seconds) {
        this.plugin = plugin;
        this.messages = messages;
        this.seconds = seconds;
    }

    /** False if the player stands or already glides; the item is used up otherwise. */
    boolean use(Player player) {
        if (grounded(player.getLocation())) {
            player.sendMessage(messages.get("items.parachute.grounded"));
            return false;
        }
        if (open.containsKey(player.getUniqueId())) {
            player.sendMessage(messages.get("items.parachute.already"));
            return false;
        }
        var chicken = player.getWorld().spawn(player.getEyeLocation(), Chicken.class, spawned -> spawned.setPersistent(false));
        player.addPassenger(chicken);
        player.setVelocity(new Vector());
        var expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(messages.get("items.parachute.broke"));
            close(player);
        }, seconds * 20L);
        open.put(player.getUniqueId(), new Canopy(chicken, expiry));
        return true;
    }

    /** While falling the player glides; on the ground the parachute is gone. */
    void moved(Player player, Location from, Location to) {
        if (!open.containsKey(player.getUniqueId())) {
            return;
        }
        if (grounded(to)) {
            close(player);
            return;
        }
        double fall = to.getY() - from.getY();
        if (fall >= 0.0) {
            return;
        }
        double yaw = Math.toRadians(to.getYaw());
        double horizontal = Math.cos(Math.toRadians(to.getPitch()));
        double speed = player.getWalkSpeed() * GLIDE;
        player.setVelocity(new Vector(-horizontal * Math.sin(yaw) * speed, fall * SINK, horizontal * Math.cos(yaw) * speed));
        player.setFallDistance(0.0f);
    }

    /** The chicken died, so the parachute goes without a word; true if it was one. */
    boolean shotDown(Entity entity) {
        var owner = open.entrySet().stream().filter(entry -> entry.getValue().chicken().equals(entity)).findFirst();
        owner.ifPresent(entry -> {
            entry.getValue().expiry().cancel();
            open.remove(entry.getKey());
        });
        return owner.isPresent();
    }

    void close(Player player) {
        Optional.ofNullable(open.remove(player.getUniqueId())).ifPresent(canopy -> {
            canopy.expiry().cancel();
            var chicken = canopy.chicken();
            chicken.getWorld().spawnParticle(Particle.LARGE_SMOKE, chicken.getLocation(), 1, 0, 0, 0, 0);
            chicken.remove();
        });
    }

    /** Standing on something; the client's own on-ground flag is not to be trusted. */
    private static boolean grounded(Location feet) {
        return feet.clone().subtract(0, GROUND_PROBE, 0).getBlock().isSolid();
    }
}
