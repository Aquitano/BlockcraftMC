package me.blockcraft.api.xp;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.xp.XpRepository;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
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

    private static final Sound LEVEL_UP = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 1.0f, 1.0f);

    private final Plugin plugin;
    private final XpRepository repository;
    private final Levels levels;
    private final Map<XpReward, Integer> rewards;
    private final Messages messages;
    private final Map<UUID, Long> totals = new ConcurrentHashMap<>();

    public Xp(Plugin plugin, XpRepository repository, Levels levels, Map<XpReward, Integer> rewards, Messages messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.levels = levels;
        this.rewards = rewards;
        this.messages = messages;
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

    public void award(Player player, XpReward reward) {
        award(player, rewards.getOrDefault(reward, 0));
    }

    public void award(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> repository.add(uuid, amount), total -> {
            totals.put(uuid, total);
            if (!player.isOnline()) {
                return;
            }
            player.sendActionBar(messages.get("xp.gained", Formatter.number("amount", amount)));
            int before = levels.levelFor(total - amount);
            int after = levels.levelFor(total);
            if (after > before) {
                player.sendMessage(messages.get("xp.level-up", Formatter.number("level", after)));
                player.playSound(LEVEL_UP);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        Tasks.async(plugin, () -> repository.get(uuid), total -> totals.merge(uuid, total, Math::max));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        totals.remove(event.getPlayer().getUniqueId());
    }
}
