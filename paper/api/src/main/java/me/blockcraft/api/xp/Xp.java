package me.blockcraft.api.xp;

import me.blockcraft.api.notification.Notifications;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.xp.XpRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network XP and levels. Totals are cached per online player so chat and menus can show the level without a
 * query; awards go to the database first and the cache follows, so a level-up is judged on the stored total.
 */
public final class Xp implements Listener {

    private final Plugin plugin;
    private final XpRepository repository;
    private final Levels levels;
    private final Map<XpReward, Integer> rewards;
    private final Notifications notifications;
    private final Map<UUID, Object> sessions = new java.util.HashMap<>();
    private final Map<UUID, Long> totals = new ConcurrentHashMap<>();

    public Xp(Plugin plugin, XpRepository repository, Levels levels, Map<XpReward, Integer> rewards,
              Notifications notifications) {
        this.plugin = plugin;
        this.repository = repository;
        this.levels = levels;
        this.rewards = rewards;
        this.notifications = notifications;
    }

    public Levels levels() {
        return levels;
    }

    /** The cached total, 0 until the join-time load finished. */
    public long total(Player player) {
        return totals.getOrDefault(player.getUniqueId(), 0L);
    }

    public int level(Player player) {
        return levels.levelFor(total(player));
    }

    /** Returns what the reward is worth, so a round summary can name the XP it handed out. */
    public int award(Player player, XpReward reward) {
        int amount = rewards.getOrDefault(reward, 0);
        award(player, amount);
        return amount;
    }

    public void award(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        var uuid = player.getUniqueId();
        var session = sessions.get(uuid);
        Tasks.async(plugin, () -> repository.add(uuid, amount), total -> {
            if (!player.isOnline() || sessions.get(uuid) != session) {
                return;
            }
            long cached = totals.merge(uuid, total, Math::max);
            int before = levels.levelFor(total - amount);
            int after = levels.levelFor(total);
            notifications.xp(player, amount, after > before ? levels.levelFor(cached) : 0);
            if (after > before) {
                plugin.getServer().getPluginManager().callEvent(new LevelUpEvent(player, after));
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        var session = new Object();
        sessions.put(uuid, session);
        Tasks.async(plugin, () -> repository.get(uuid), total -> {
            if (sessions.get(uuid) == session) {
                totals.merge(uuid, total, Math::max);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        totals.remove(event.getPlayer().getUniqueId());
    }
}
