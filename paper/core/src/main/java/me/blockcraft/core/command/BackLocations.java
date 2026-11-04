package me.blockcraft.core.command;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Teleports made by core's staff commands, remembering where each moved player stood for {@code /back}. */
public final class BackLocations implements Listener {

    private final Map<UUID, Location> previous = new HashMap<>();

    boolean teleport(Entity entity, Location destination) {
        var from = entity.getLocation();
        if (!entity.teleport(destination, TeleportCause.COMMAND)) {
            return false;
        }
        if (entity instanceof Player player) {
            previous.put(player.getUniqueId(), from);
        }
        return true;
    }

    Optional<Location> of(Player player) {
        return Optional.ofNullable(previous.get(player.getUniqueId()));
    }

    static String describe(Location location) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        previous.remove(event.getPlayer().getUniqueId());
    }
}
