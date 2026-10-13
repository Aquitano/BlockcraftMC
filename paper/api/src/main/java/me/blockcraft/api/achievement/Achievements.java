package me.blockcraft.api.achievement;

import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.menu.AchievementsMenu;
import me.blockcraft.api.notification.Notifications;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.xp.LevelUpEvent;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The achievement catalog and what earns an entry of it. A game's entries are judged on its stats table after
 * every round, the network's on the level after every level-up, and a moment whenever the code that saw it calls
 * {@link #grant}; whatever is reached and not yet owned becomes one {@code achievement.<game>.<name>} unlock, pays
 * its XP and is announced to the player. Every read of the totals runs off the main thread.
 */
public final class Achievements implements Listener {

    /** Where a player stands on every entry: the totals the catalog counts and what they own already. */
    public record Snapshot(Map<String, Stats> stats, int level, Set<String> owned) {

        public long current(Achievement achievement) {
            return achievement.metric().of(stats.getOrDefault(achievement.game(), Stats.EMPTY), level);
        }

        public boolean owns(Achievement achievement) {
            return owned.contains(achievement.item());
        }
    }

    private record Target(Player player, int level) {
    }

    /** The games write their stats asynchronously around the summary, so the totals get a moment to land. */
    private static final long ROUND_SETTLE_TICKS = 40L;
    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500));

    private final Plugin plugin;
    private final List<Achievement> catalog;
    private final Map<String, StatsRepository> games;
    private final Unlocks unlocks;
    private final Xp xp;
    private final Messages messages;
    private final PlayerTasks playerTasks;
    private final Notifications notifications;

    public Achievements(Plugin plugin, List<Achievement> catalog, Map<String, StatsRepository> games, Unlocks unlocks,
                        Xp xp, Messages messages, PlayerTasks playerTasks, Notifications notifications) {
        this.plugin = plugin;
        this.catalog = List.copyOf(catalog);
        this.games = games;
        this.unlocks = unlocks;
        this.xp = xp;
        this.messages = messages;
        this.playerTasks = playerTasks;
        this.notifications = notifications;
    }

    /** {@code achievements.<game>.<name>: {counts, threshold, xp}} of config.yml, in file order. */
    public static List<Achievement> parse(Map<String, Object> section) {
        var catalog = new ArrayList<Achievement>();
        section.keySet().forEach(game -> YamlFile.section(section, game).keySet().forEach(name -> {
            var entry = YamlFile.section(YamlFile.section(section, game), name);
            var counts = String.valueOf(entry.get("counts"));
            var metric = Metric.parse(counts).orElseThrow(() ->
                    new IllegalArgumentException("achievements." + game + "." + name + " counts unknown metric " + counts));
            catalog.add(new Achievement(game, name, metric, number(entry, "threshold"), number(entry, "xp")));
        }));
        return catalog;
    }

    /** Whatever the totals reach that is not owned yet, in catalog order; the whole judgement, without a database. */
    public static List<Achievement> reached(List<Achievement> catalog, String game, Stats stats, int level, Set<String> owned) {
        return catalog.stream()
                .filter(achievement -> achievement.game().equals(game))
                .filter(achievement -> achievement.reached(achievement.metric().of(stats, level)))
                .filter(achievement -> !owned.contains(achievement.item()))
                .toList();
    }

    public List<Achievement> catalog() {
        return catalog;
    }

    public int threshold(String game, String name) {
        return catalog.stream().filter(entry -> entry.game().equals(game) && entry.name().equals(name))
                .mapToInt(Achievement::threshold).findFirst().orElse(Integer.MAX_VALUE);
    }

    /** The games with entries, in catalog order; {@link Achievement#NETWORK} among them. */
    public Set<String> games() {
        var games = new LinkedHashSet<String>();
        catalog.forEach(achievement -> games.add(achievement.game()));
        return games;
    }

    public List<Achievement> of(String game) {
        return catalog.stream().filter(achievement -> achievement.game().equals(game)).toList();
    }

    /**
     * Earns the moment {@code achievements.<game>.<name>} for the player, once; a name the catalog lacks, or one that
     * counts something other than moments, earns nothing.
     */
    public void grant(Player player, String game, String name) {
        var entry = catalog.stream()
                .filter(achievement -> achievement.isMoment() && achievement.game().equals(game) && achievement.name().equals(name))
                .findFirst();
        if (entry.isEmpty()) {
            plugin.getLogger().warning("No moment achievement " + game + "." + name + " in the catalog");
            return;
        }
        var achievement = entry.get();
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, () -> unlocks.unlock(uuid, achievement.item()), fresh -> {
            if (fresh) {
                celebrate(player, List.of(achievement));
            }
        });
    }

    public void afterRound(String game, Collection<? extends Player> participants) {
        List<Player> players = List.copyOf(participants);
        Tasks.later(plugin, ROUND_SETTLE_TICKS, () -> evaluate(game, players.stream().filter(Player::isOnline).toList()));
    }

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        evaluate(Achievement.NETWORK, List.of(event.getPlayer()));
    }

    public void open(Player player, Optional<Consumer<Player>> back) {
        var uuid = player.getUniqueId();
        int level = xp.level(player);
        playerTasks.latest(player, () -> {
            var stats = new LinkedHashMap<String, Stats>();
            games().forEach(game -> Optional.ofNullable(games.get(game)).ifPresent(repository -> stats.put(game, repository.get(uuid))));
            return new Snapshot(stats, level, unlocks.owned(uuid, Achievement.ITEM_PREFIX));
        }, snapshot -> new AchievementsMenu(this, messages, snapshot, back).open(player));
    }

    private void evaluate(String game, List<Player> players) {
        if (players.isEmpty() || of(game).isEmpty()) {
            return;
        }
        var repository = games.get(game);
        var targets = players.stream().map(player -> new Target(player, xp.level(player))).toList();
        Tasks.async(plugin, () -> {
            var unlocked = new LinkedHashMap<Player, List<Achievement>>();
            for (var target : targets) {
                UUID uuid = target.player().getUniqueId();
                var stats = repository == null ? Stats.EMPTY : repository.get(uuid);
                var owned = unlocks.owned(uuid, Achievement.ITEM_PREFIX);
                var fresh = reached(catalog, game, stats, target.level(), owned).stream()
                        .filter(achievement -> unlocks.unlock(uuid, achievement.item()))
                        .toList();
                if (!fresh.isEmpty()) {
                    unlocked.put(target.player(), fresh);
                }
            }
            return unlocked;
        }, unlocked -> unlocked.forEach(this::celebrate));
    }

    private void celebrate(Player player, List<Achievement> unlocked) {
        for (var achievement : unlocked) {
            xp.award(player, achievement.xp());
            if (player.isOnline()) {
                player.sendMessage(messages.get("achievements.unlocked",
                        Placeholder.component("name", name(achievement)), Formatter.number("xp", achievement.xp())));
            }
        }
        if (!player.isOnline()) {
            return;
        }
        var last = unlocked.getLast();
        notifications.reward(player,
                Title.title(messages.get("achievements.title"),
                        messages.get("achievements.subtitle", Placeholder.component("name", name(last))), TIMES), Sounds.ACHIEVEMENT);
    }

    public Component name(Achievement achievement) {
        return messages.get("achievements." + achievement.key() + ".name");
    }

    public Component description(Achievement achievement) {
        return messages.get("achievements." + achievement.key() + ".description");
    }

    private static int number(Map<String, Object> entry, String key) {
        return entry.get(key) instanceof Number value ? value.intValue() : 0;
    }
}
