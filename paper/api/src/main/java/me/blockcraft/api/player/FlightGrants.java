package me.blockcraft.api.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who was given flight on purpose ({@code /fly}, staff mode), as opposed to the flag the lobby sets for double jump,
 * which leaves these players' flight alone. It ends with the session.
 */
public final class FlightGrants implements Listener {

    private final Set<UUID> granted = new HashSet<>();

    public boolean granted(Player player) {
        return granted.contains(player.getUniqueId());
    }

    public void set(Player player, boolean granted) {
        if (granted) {
            this.granted.add(player.getUniqueId());
        } else {
            this.granted.remove(player.getUniqueId());
        }
    }

    // Last, because staff mode hands back the grant it took while the player quits.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        granted.remove(event.getPlayer().getUniqueId());
    }
}
