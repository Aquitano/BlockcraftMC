package me.blockcraft.api.achievement;

import me.blockcraft.api.cosmetic.ChatTagChoice;
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
import me.blockcraft.api.xp.XpLoadedEvent;
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
import java.util.Comparator;
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

    private record Rewards(List<Achievement> achievements, List<ChatTagChoice> tags) {
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

    /** {@code achievements.<game>.<name>: {counts, threshold, xp, tag?}} of config.yml, in file order. */
    public static List<Achievement> parse(Map<String, Object> section) {
        var catalog = new ArrayList<Achievement>();
        section.keySet().forEach(game -> YamlFile.section(section, game).keySet().forEach(name -> {
            var entry = YamlFile.section(YamlFile.section(section, game), name);
            var counts = String.valueOf(entry.get("counts"));
            var metric = Metric.parse(counts).orElseThrow(() ->
                    new IllegalArgumentException("achievements." + game + "." + name + " counts unknown metric " + counts));
            var tag = Optional.ofNullable(entry.get("tag")).map(value -> ChatTagChoice.parse(String.valueOf(value))
                    .filter(choice -> choice.tag().earned()).orElseThrow(() ->
                            new IllegalArgumentException("Invalid earned tag for achievements." + game + "." + name)));
            if (tag.isPresent() && (!game.equals(Achievement.NETWORK) || metric != Metric.LEVEL)) {
                throw new IllegalArgumentException("Tag rewards require a network level achievement: " + game + "." + name);
            }
            catalog.add(new Achievement(game, name, metric, number(entry, "threshold"), number(entry, "xp"), tag));
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

    public static List<ChatTagChoice> missingTags(List<Achievement> catalog, int level, Set<String> owned) {
        return catalog.stream()
                .filter(achievement -> achievement.game().equals(Achievement.NETWORK) && achievement.metric() == Metric.LEVEL)
                .filter(achievement -> achievement.reached(level))
                .flatMap(achievement -> achievement.tag().stream())
                .distinct()
                .filter(tag -> !owned.contains(tag.item()))
                .toList();
    }

    public static Optional<Achievement> nextCosmetic(List<Achievement> catalog, int level) {
        return catalog.stream()
                .filter(achievement -> achievement.game().equals(Achievement.NETWORK) && achievement.metric() == Metric.LEVEL)
                .filter(achievement -> achievement.tag().isPresent() && achievement.threshold() > level)
                .min(Comparator.comparingInt(Achievement::threshold));
    }

    public Component nextCosmetic(int level) {
        return nextCosmetic(catalog, level).map(achievement -> messages.get("level.next-cosmetic",
                Formatter.number("level", achievement.threshold()),
                Placeholder.component("tag", achievement.tag().orElseThrow().text(messages))))
                .orElseGet(() -> messages.get("level.cosmetics-complete"));
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

    @EventHandler
    public void onXpLoaded(XpLoadedEvent event) {
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
            var unlocked = new LinkedHashMap<Player, Rewards>();
            for (var target : targets) {
                UUID uuid = target.player().getUniqueId();
                var stats = repository == null ? Stats.EMPTY : repository.get(uuid);
                var owned = unlocks.owned(uuid, Achievement.ITEM_PREFIX);
                var tags = game.equals(Achievement.NETWORK)
                        ? missingTags(catalog, target.level(), unlocks.owned(uuid, ChatTagChoice.ITEM_PREFIX)).stream()
                                .filter(tag -> unlocks.unlock(uuid, tag.item())).toList()
                        : List.<ChatTagChoice>of();
                var fresh = reached(catalog, game, stats, target.level(), owned).stream()
                        .filter(achievement -> unlocks.unlock(uuid, achievement.item()))
                        .toList();
                if (!fresh.isEmpty() || !tags.isEmpty()) {
                    unlocked.put(target.player(), new Rewards(fresh, tags));
                }
            }
            return unlocked;
        }, unlocked -> unlocked.forEach((player, rewards) -> {
            if (!rewards.achievements().isEmpty()) {
                celebrate(player, rewards.achievements());
            }
            if (plugin.getServer().getPlayer(player.getUniqueId()) == player) {
                rewards.tags().forEach(tag -> player.sendMessage(messages.get("achievements.tag-unlocked",
                        Placeholder.component("tag", tag.text(messages)))));
            }
        }));
    }

    private void celebrate(Player player, List<Achievement> unlocked) {
        boolean currentSession = plugin.getServer().getPlayer(player.getUniqueId()) == player;
        for (var achievement : unlocked) {
            xp.award(player, achievement.xp());
            if (currentSession) {
                player.sendMessage(messages.get("achievements.unlocked",
                        Placeholder.component("name", name(achievement)), Formatter.number("xp", achievement.xp())));
            }
        }
        if (!currentSession) {
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
        return messages.get("achievements." + achievement.key() + ".description",
                Formatter.number("level", achievement.threshold()),
                Placeholder.component("tag", achievement.tag().map(tag -> tag.text(messages)).orElse(Component.empty())));
    }

    private static int number(Map<String, Object> entry, String key) {
        return entry.get(key) instanceof Number value ? value.intValue() : 0;
    }
}
