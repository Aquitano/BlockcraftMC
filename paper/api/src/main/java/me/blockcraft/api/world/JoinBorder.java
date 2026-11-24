package me.blockcraft.api.world;

import me.blockcraft.api.task.Tasks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Paper sends the border of the world a player logged into once more after {@link PlayerJoinEvent}. A join listener
 * that moves the player into another world, such as the waiting lobby, would leave them with the first world's border
 * there, so the border of the world they are in follows a tick later. A border of the player's own stays.
 */
public final class JoinBorder implements Listener {

    private final Plugin plugin;

    public JoinBorder(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        Tasks.sync(plugin, () -> {
            if (player.isOnline() && player.getWorldBorder() == null) {
                player.setWorldBorder(null);
            }
        });
    }
}
