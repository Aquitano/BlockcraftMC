package me.blockcraft.api.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The account name of every online player. A nick swaps the player's profile, so {@link Player#getName()} follows
 * the nick from then on; rows keyed by name (stats, coins) want the name the player logged in with.
 */
public final class RealNames implements Listener {

    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    public String of(Player player) {
        return names.getOrDefault(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        names.put(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        names.remove(event.getPlayer().getUniqueId());
    }
}
