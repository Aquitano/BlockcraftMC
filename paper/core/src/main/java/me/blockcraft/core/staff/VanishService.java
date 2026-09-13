package me.blockcraft.core.staff;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.moderation.VanishRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network-wide vanish: the flag lives in {@code bc_vanished}, is read during the async pre-login so the join
 * event can already act on it, and is only changed by {@link #set}. Staff with {@link #SEE_PERMISSION} and
 * other vanished players keep seeing vanished players. The proxy hears about every change so its lists and
 * counts agree; it reads the table itself for players who log in vanished.
 */
public final class VanishService implements Listener {

    public static final String SEE_PERMISSION = "blockcraft.core.vanish.see";

    private final Plugin plugin;
    private final VanishRepository repository;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public VanishService(Plugin plugin, VanishRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
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
        Tasks.async(plugin, () -> repository.set(uuid, vanish));
        ProxyMessaging.vanish(plugin, player, vanish);
    }

    private boolean canSee(Player viewer, Player hidden) {
        return viewer.equals(hidden) || viewer.hasPermission(SEE_PERMISSION) || vanished.contains(viewer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (repository.isVanished(event.getUniqueId())) {
            pending.add(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        var joiner = event.getPlayer();
        if (pending.remove(joiner.getUniqueId())) {
            vanished.add(joiner.getUniqueId());
            event.joinMessage(null);
            Bukkit.getOnlinePlayers().forEach(viewer -> {
                if (!canSee(viewer, joiner)) {
                    viewer.hidePlayer(plugin, joiner);
                }
            });
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
