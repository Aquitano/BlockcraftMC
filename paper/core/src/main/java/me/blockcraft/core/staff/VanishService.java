package me.blockcraft.core.staff;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.SerialTasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.moderation.VanishRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network-wide vanish: the flag lives in {@code bc_vanished}, BlockcraftAPI reads it during the async pre-login so
 * the join event can already act on it, and it is only changed by {@link #set}. Staff with {@link #SEE_PERMISSION} and
 * other vanished players keep seeing vanished players. The proxy hears about every change so its lists and
 * counts agree; it reads the table itself for players who log in vanished.
 */
public final class VanishService implements Listener {

    public static final String SEE_PERMISSION = "blockcraft.core.vanish.see";

    private final Plugin plugin;
    private final VanishRepository repository;
    private final SerialTasks<UUID> writes;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(Plugin plugin, VanishRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.writes = new SerialTasks<>(plugin);
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    /** Returns whether the player is vanished after the toggle. */
    public boolean toggle(Player player) {
        boolean now = !isVanished(player);
        set(player, now);
        return now;
    }

    public void set(Player player, boolean vanish) {
        var uuid = player.getUniqueId();
        if (vanish) {
            vanished.add(uuid);
            Bukkit.getOnlinePlayers().forEach(viewer -> {
                if (!canSee(viewer, player)) {
                    viewer.hidePlayer(plugin, player);
                }
            });
        } else {
            vanished.remove(uuid);
            Bukkit.getOnlinePlayers().forEach(viewer -> viewer.showPlayer(plugin, player));
        }
        writes.run(uuid, () -> repository.set(uuid, vanish));
        ProxyMessaging.vanish(plugin, player, vanish);
        Bukkit.getPluginManager().callEvent(new VanishChangeEvent(player, vanish));
    }

    private boolean canSee(Player viewer, Player hidden) {
        return viewer.equals(hidden) || viewer.hasPermission(SEE_PERMISSION) || vanished.contains(viewer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        var joiner = event.getPlayer();
        if (BlockcraftApi.get().isVanished(joiner)) {
            vanished.add(joiner.getUniqueId());
            event.joinMessage(null);
            Bukkit.getOnlinePlayers().forEach(viewer -> {
                if (!canSee(viewer, joiner)) {
                    viewer.hidePlayer(plugin, joiner);
                }
            });
            Bukkit.getPluginManager().callEvent(new VanishChangeEvent(joiner, true));
        }
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !canSee(joiner, hidden)) {
                joiner.hidePlayer(plugin, hidden);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.remove(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }
}
