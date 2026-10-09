package me.blockcraft.api.afk;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.notification.Notifications;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is away from the keyboard: whoever ran /afk, or did not move, look around, chat, run a command or interact
 * for the configured idle time. The next such activity brings them back. The player sees it in the action bar,
 * the tab list marks them and the proxy hears every change; nobody else is told in chat.
 */
public final class Afk implements Listener {

    private static final long CHECK_TICKS = 20L;

    private final Plugin plugin;
    private final Notifications notifications;
    private final Messages messages;
    private final Duration idle;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Set<UUID> away = ConcurrentHashMap.newKeySet();

    public Afk(Plugin plugin, Notifications notifications, Messages messages, Duration idle) {
        this.plugin = plugin;
        this.notifications = notifications;
        this.messages = messages;
        this.idle = idle;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    public boolean isAfk(Player player) {
        return away.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        set(player, !isAfk(player));
    }

    private void check() {
        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAfk(player)) {
                notifications.status(player, messages.get("afk.status"));
            } else if (now - lastActive.getOrDefault(player.getUniqueId(), now) >= idle.toNanos()) {
                set(player, true);
            }
        }
    }

    private void set(Player player, boolean afk) {
        var uuid = player.getUniqueId();
        if (!(afk ? away.add(uuid) : away.remove(uuid))) {
            return;
        }
        lastActive.put(uuid, System.nanoTime());
        ProxyMessaging.afk(plugin, player, afk);
        new AfkChangeEvent(player, afk).callEvent();
        if (afk) {
            notifications.status(player, messages.get("afk.status"));
        } else {
            notifications.feedback(player, messages.get("afk.back"));
        }
    }

    private void active(Player player) {
        lastActive.put(player.getUniqueId(), System.nanoTime());
        if (!isAfk(player)) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            set(player, false);
        } else {
            Tasks.sync(plugin, () -> {
                if (player.isOnline()) {
                    set(player, false);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock() || event.hasChangedOrientation()) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        active(event.getPlayer());
    }

    /** /afk itself is no sign of being back, or it would end the AFK it just started. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0];
        if (!label.substring(label.indexOf(':') + 1).equalsIgnoreCase(AfkCommand.NAME)) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        active(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastActive.put(event.getPlayer().getUniqueId(), System.nanoTime());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
        away.remove(event.getPlayer().getUniqueId());
    }
}
