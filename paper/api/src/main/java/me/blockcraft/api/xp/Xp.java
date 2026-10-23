package me.blockcraft.api.xp;

import me.blockcraft.api.notification.Notifications;
import me.blockcraft.api.task.JoinRetry;
import me.blockcraft.api.task.SerialTasks;
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
 * Awards to one player run in order, because the stored total after one must not already include the next.
 */
public final class Xp implements Listener {

    private final Plugin plugin;
    private final XpRepository repository;
    private final Levels levels;
    private final Map<XpReward, Integer> rewards;
    private final Notifications notifications;
    private final Map<UUID, Object> sessions = new java.util.HashMap<>();
    private final Map<UUID, Long> totals = new ConcurrentHashMap<>();
    private final SerialTasks<UUID> awards;

    public Xp(Plugin plugin, XpRepository repository, Levels levels, Map<XpReward, Integer> rewards,
              Notifications notifications) {
        this.plugin = plugin;
        this.repository = repository;
        this.levels = levels;
        this.rewards = rewards;
        this.notifications = notifications;
        this.awards = new SerialTasks<>(plugin);
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
        awards.submit(uuid, () -> repository.add(uuid, amount), total -> {
            var online = plugin.getServer().getPlayer(uuid);
            if (online == null || !sessions.containsKey(uuid)) {
                return;
            }
            long cached = totals.merge(uuid, total, Math::max);
            int before = levels.levelFor(total - amount);
            int after = levels.levelFor(total);
            if (online == player && sessions.get(uuid) == session) {
                notifications.xp(player, amount, after > before ? levels.levelFor(cached) : 0);
            }
            if (after > before) {
                plugin.getServer().getPluginManager().callEvent(new LevelUpEvent(online, levels.levelFor(cached)));
            }
        }, () -> { });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var session = new Object();
        sessions.put(event.getPlayer().getUniqueId(), session);
        load(event.getPlayer(), session, 0);
    }

    private void load(Player player, Object session, int failures) {
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.get(uuid), total -> {
            if (sessions.get(uuid) == session) {
                totals.merge(uuid, total, Math::max);
                plugin.getServer().getPluginManager().callEvent(new XpLoadedEvent(player));
            }
        }, () -> {
            if (sessions.get(uuid) == session) {
                JoinRetry.after(plugin, player, failures, () -> load(player, session, failures + 1));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        totals.remove(event.getPlayer().getUniqueId());
    }
}
