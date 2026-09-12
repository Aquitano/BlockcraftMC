package me.blockcraft.core.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Who is vanished right now; players joining later are kept from seeing them too. Vanish ends on quit. */
public final class VanishListener implements Listener {

    private final Plugin plugin;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Returns whether the player is vanished after the toggle. */
    public boolean toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            Bukkit.getOnlinePlayers().forEach(other -> other.showPlayer(plugin, player));
            return false;
        }
        vanished.add(player.getUniqueId());
        Bukkit.getOnlinePlayers().forEach(other -> other.hidePlayer(plugin, player));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null) {
                event.getPlayer().hidePlayer(plugin, hidden);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        vanished.remove(event.getPlayer().getUniqueId());
    }
}
