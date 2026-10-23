package me.blockcraft.api.ignore;

import me.blockcraft.api.task.JoinRetry;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.ignore.IgnoreRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Whom each online player ignores, so chat can leave those players out. The proxy owns {@code bc_ignores}: the
 * list is read when the player joins this server, and the proxy reports every later /ignore over the plugin channel.
 * Chat reads it off the main thread.
 */
public final class Ignores implements Listener {

    private final Plugin plugin;
    private final IgnoreRepository repository;
    private final IgnoreCache cache = new IgnoreCache();

    public Ignores(Plugin plugin, IgnoreRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public boolean ignores(Player player, Player other) {
        return cache.ignores(player.getUniqueId(), other.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer(), 0);
    }

    private void load(Player player, int failures) {
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.list(uuid).stream().map(IgnoreRepository.Ignored::uuid).toList(), stored -> {
            if (player.isOnline()) {
                cache.load(uuid, stored);
            }
        }, () -> {
            if (player.isOnline()) {
                JoinRetry.after(plugin, player, failures, () -> load(player, failures + 1));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.forget(event.getPlayer().getUniqueId());
    }

    public void update(Player player, UUID other, boolean ignoring) {
        cache.set(player.getUniqueId(), other, ignoring);
    }
}
