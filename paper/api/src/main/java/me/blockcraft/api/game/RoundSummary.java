package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.achievement.Achievement;
import me.blockcraft.api.coins.Coins;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.task.SerialTasks;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.round.RoundRecap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * What a round paid and who won it. A game feeds it while the round runs and calls {@link #show()} once at the
 * end; because every coin and every XP point of a round is handed out here, the recap names exactly what was
 * paid, keeps the player's numbers and notes behind Details, and offers another round or the lobby. Kill streaks
 * are counted here too and announced at the lengths {@link Streaks} names. The recap is also stored for every
 * participant, so the lobby can show it on request. Their numbers go into the game's stats table in one write:
 * the coins as points, and a loss for everyone knocked out or
 * beaten by a winner.
 *
 * <p>Every kill and death passes through here, so here they also become what the network makes of them: the
 * victim's title, the {@link Rivalries} of the players, the respects paid with an F in chat and the report button
 * for a cry of hacker.
 */
public final class RoundSummary {

    /** What a kill and a win pay; the amounts are the game's own {@code rewards} keys in its config.yml. */
    public record Rewards(int kill, int win) {
    }

    private static final Title.Times MOMENT = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500));
    private static final Title.Times RESULT = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(350));
    private static final Title.Times RETURN_SECOND = Title.Times.times(Duration.ZERO, Duration.ofMillis(750), Duration.ofMillis(150));
    static final double HALF_A_HEART = 1.0;

    /** The buttons stay clickable past the restart countdown, which every game's config.yml sets for itself. */
    private static final ClickCallback.Options BUTTON = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(1))
            .build();

    private final Plugin plugin;
    private final BlockcraftApi api;
    private final Component game;
    private final Messages messages;
    private final Rewards rewards;
    private final StatsRepository stats;
    private final Map<UUID, Score> scores = new LinkedHashMap<>();
    private final Map<UUID, String> winners = new LinkedHashMap<>();
    private String opponentWinner;
    private final Set<UUID> resultViewers = new HashSet<>();
    private final Rivalries rivalries;
    private final Respects respects;
    private final Accusations accusations;
    private final DeathLines deathLines;
    private final RoundTracking tracking;
    private final SerialTasks<UUID> recapWrites;
    private boolean closed;
    private boolean resultsShown;
    private boolean returning;

    private record Row(UUID player, String name, Stats delta) {
    }

    /** {@code gameMessages} is the game's own file; it names the game as {@code game-name} and lends its prefix. */
    public RoundSummary(Plugin plugin, Messages gameMessages, Rewards rewards, StatsRepository stats) {
        this(plugin, gameMessages, rewards, stats, new SerialTasks<>(plugin));
    }

    public RoundSummary(Plugin plugin, Messages gameMessages, Rewards rewards, StatsRepository stats,
                        SerialTasks<UUID> recapWrites) {
        this.recapWrites = recapWrites;
        this.plugin = plugin;
        this.api = BlockcraftApi.get();
        this.game = gameMessages.get("game-name");
        this.messages = api.messages().prefixedLike(gameMessages);
        this.rewards = rewards;
        this.stats = stats;
        var config = api.roundConfig();
        this.rivalries = new Rivalries(plugin, api, messages, new RivalryRepository(api.database()), config.nemesisLead());
        this.respects = new Respects(plugin, messages, config.respectWindow());
        this.accusations = new Accusations(plugin, messages, config.accusationWindow(), config.accusations());
        this.deathLines = new DeathLines(messages);
        this.tracking = new RoundTracking(plugin, config.creditWindow(), config.curiosityMinimums());
        Bukkit.getPluginManager().registerEvents(tracking, plugin);
        Bukkit.getPluginManager().registerEvents(respects, plugin);
        Bukkit.getPluginManager().registerEvents(accusations, plugin);
    }

    /** The round has started with these players; their rivalries load, and each hears of a nemesis among them. */
    void begin(List<Player> players) {
        tracking.begin(players);
        rivalries.load(players);
    }

    /** The death's line, and the killer a recent hit credits; asking uses that credit up. */
    public DeathLines.Death resolveDeath(PlayerDeathEvent event) {
        return deathLines.resolve(event, tracking.takeHit(event.getPlayer()));
    }

    /** Whoever joins the round as a player; everyone else is a spectator and only sees who won. */
    public void join(Player player) {
        score(player);
    }

    /** A player who left before the round started takes no part in it. */
    public void forget(Player player) {
        scores.remove(player.getUniqueId());
        tracking.forget(player);
    }

    /** Out of the round for good, which books a loss whoever wins. */
    public void eliminate(Player player) {
        score(player).eliminated = true;
    }

    /**
     * Pays the killer and plays their kill effect; the victim sees who beat them with how much health left, headed
     * by what the kill cost them.
     */
    public void awardKill(Player killer, Player victim, Defeat defeat) {
        api.effects().onKill(killer, victim);
        Sounds.KILL.play(killer);
        var score = score(killer);
        score.kills++;
        score.streak++;
        score.bestStreak = Math.max(score.bestStreak, score.streak);
        if (api.streaks().announces(score.streak)) {
            Bukkit.broadcast(messages.get("summary.streak",
                    Placeholder.unparsed("player", killer.getName()), Formatter.number("streak", score.streak)));
            Sounds.STREAK.play(killer);
        }
        score.xp += api.xp().award(killer, XpReward.KILL);
        pay(killer, rewards.kill());
        moment(victim, messages.get(switch (defeat) {
            case OUT -> "defeat.out";
            case LIFE -> "defeat.life";
            case POINT -> "defeat.point";
            case RESPAWN -> "defeat.respawn";
        }), messages.get("defeat.subtitle", Placeholder.unparsed("killer", killer.getName()),
                Placeholder.unparsed("hearts", hearts(killer.getHealth()))));
        rivalries.kill(killer, victim, standing -> avenge(killer, victim, standing));
        accusations.killed(victim, killer);
    }

    /** A line of the player's own recap, shown after their numbers. */
    public void note(Player player, Component line) {
        score(player).notes.add(line);
    }

    public int kills(Player player) {
        var score = scores.get(player.getUniqueId());
        return score == null ? 0 : score.kills;
    }

    /** Cooperative mob kills count toward stats without player rivalries or PvP kill XP. */
    public void awardMobKill(Player player) {
        score(player).kills++;
        pay(player, rewards.kill());
    }

    public boolean undamaged(Player player) {
        return tracking.undamaged(player);
    }

    /** Starts another game within this round, retaining cumulative rewards, stats and the best kill streak. */
    public void nextGame() {
        tracking.nextGame();
        scores.values().forEach(score -> score.streak = 0);
    }

    /** Grants the moment achievement {@code game.name} and adds its {@code moments.<name>} line to the player's recap. */
    public void moment(Player player, String game, String name, TagResolver... resolvers) {
        note(player, messages.get("moments." + name, resolvers));
        api.achievements().grant(player, game, name);
    }

    public void minedEmeralds(Player player, int amount) {
        tracking.minedEmeralds(player, amount);
    }

    public List<String> winnerNames() {
        return opponentWinner == null ? List.copyOf(winners.values()) : List.of(opponentWinner);
    }

    public void defeatedBy(String opponent) {
        opponentWinner = opponent;
    }

    public void death(Player victim) {
        tracking.died(victim);
        var score = score(victim);
        score.deaths++;
        score.streak = 0;
        respects.died(victim);
    }

    /** Pays the winner and plays their win effect; a win on half a heart or less is a close call. */
    public void awardWin(Player winner) {
        api.effects().onWin(winner);
        winners.put(winner.getUniqueId(), winner.getName());
        var score = score(winner);
        score.xp += api.xp().award(winner, XpReward.WIN);
        pay(winner, rewards.win());
        if (!winner.isDead() && winner.getHealth() <= HALF_A_HEART) {
            moment(winner, Achievement.NETWORK, "haarscharf");
        }
    }

    /** Coins outside a kill or a win, such as a consolation for dying; they show up in the recap all the same. */
    public void awardCoins(Player player, int coins) {
        pay(player, coins);
    }

    public void show() {
        show(player -> ProxyMessaging.play(plugin, player, api.server().group()));
    }

    public void show(Consumer<Player> playAgain) {
        tracking.stop();
        var curiosity = tracking.curiosity(id -> {
            var player = Bukkit.getPlayer(id);
            return player != null && !api.isVanished(player);
        }).map(fact -> messages.get("curiosity." + fact.kind().key(),
                Placeholder.unparsed("player", Bukkit.getPlayer(fact.player()).getName()), Formatter.number("value", fact.value())));
        var participants = Bukkit.getOnlinePlayers().stream().filter(player -> scores.containsKey(player.getUniqueId())).toList();
        if (api.roundConfig().nightOwl().includes(api.calendar().now().getHour())) {
            participants.forEach(player -> moment(player, Achievement.NETWORK, "nachteule"));
        }
        participants.forEach(player -> score(player).xp += api.xp().award(player, XpReward.PARTICIPATION));
        var names = String.join(", ", winnerNames());
        store(names);
        book();
        api.achievements().afterRound(api.server().group(), participants);
        var winnerLine = winnerNames().isEmpty()
                ? messages.get("summary.draw")
                : messages.get("summary.winner", Placeholder.unparsed("winner", names));
        var spectatorResult = winnerNames().isEmpty() ? messages.get("summary.result.draw")
                : messages.get("summary.result.winner", Placeholder.unparsed("winner", names));
        resultsShown = true;
        for (var player : Bukkit.getOnlinePlayers()) {
            var score = scores.get(player.getUniqueId());
            var result = messages.get(winners.containsKey(player.getUniqueId()) ? "summary.result.win"
                    : winnerNames().isEmpty() ? "summary.result.draw" : "summary.result.lose");
            player.sendMessage(score == null
                    ? messages.get("summary.spectator", Placeholder.component("game", game),
                    Placeholder.component("result", spectatorResult))
                    : score.compact(messages, game, result));
            var details = details(score, winnerLine, curiosity);
            player.sendMessage(messages.get("summary.actions",
                    Placeholder.component("again", button("summary.again", player, () -> !closed, playAgain)),
                    Placeholder.component("lobby", button("summary.lobby", player, () -> true, viewer -> ProxyMessaging.hub(plugin, viewer))),
                    Placeholder.component("details", button("summary.details", player, () -> true, viewer -> details.forEach(viewer::sendMessage)))));
            showResult(player);
        }
    }

    void showResult(Player player) {
        if (!resultsShown || closed || returning || !player.isOnline() || player.isDead()
                || !resultViewers.add(player.getUniqueId())) {
            return;
        }
        var sound = scores.containsKey(player.getUniqueId())
                ? winners.containsKey(player.getUniqueId()) ? Sounds.WIN : Sounds.LOSE : null;
        api.notifications().important(player, resultTitle(messages, game, winners, winnerNames(), player.getUniqueId()), sound);
    }

    static Title resultTitle(Messages messages, Component game, Map<UUID, String> winners, UUID viewer) {
        return resultTitle(messages, game, winners, List.copyOf(winners.values()), viewer);
    }

    static Title resultTitle(Messages messages, Component game, Map<UUID, String> winners, List<String> names, UUID viewer) {
        if (names.isEmpty()) {
            return Title.title(messages.get("summary.ending.draw"), game, RESULT);
        }
        if (winners.containsKey(viewer)) {
            return Title.title(messages.get("summary.ending.win"), game, RESULT);
        }
        return Title.title(messages.get("summary.ending.finished"),
                messages.get(names.size() == 1 ? "summary.ending.winner" : "summary.ending.winners",
                        Placeholder.unparsed("winner", String.join(", ", names))), RESULT);
    }

    void returningIn(int seconds) {
        if (closed) {
            return;
        }
        returning = true;
        var title = Title.title(messages.get("summary.ending.seconds", Formatter.number("seconds", seconds)),
                messages.get("summary.ending.lobby"), RETURN_SECOND);
        Bukkit.getOnlinePlayers().forEach(player -> {
            api.notifications().important(player, title);
            Sounds.countdownTick(player, seconds);
        });
    }

    private List<Component> details(Score score, Component winner, Optional<Component> curiosity) {
        var lines = new ArrayList<Component>();
        lines.add(messages.get("summary.game", Placeholder.component("game", game)));
        lines.add(winner);
        if (score != null) {
            lines.addAll(score.details(messages));
        }
        curiosity.ifPresent(lines::add);
        return List.copyOf(lines);
    }

    /** The respects still being paid are said now, before the restart sends everyone away. */
    void closeRespects() {
        respects.closeAll();
        tracking.stop();
    }

    void close() {
        closed = true;
        closeRespects();
        rivalries.close();
        HandlerList.unregisterAll(tracking);
        HandlerList.unregisterAll(respects);
        HandlerList.unregisterAll(accusations);
    }

    private void avenge(Player killer, Player nemesis, Standings.Standing standing) {
        var resolvers = TagResolver.resolver(Placeholder.unparsed("nemesis", nemesis.getName()),
                Formatter.number("won", standing.won()), Formatter.number("lost", standing.lost()));
        moment(killer, messages.get("nemesis.revenge-title"), messages.get("nemesis.revenge-subtitle", resolvers));
        moment(killer, Achievement.NETWORK, "rache", resolvers);
    }

    private void moment(Player player, Component title, Component subtitle) {
        if (!closed && !resultsShown) {
            api.notifications().important(player, Title.title(title, subtitle, MOMENT));
        }
    }

    /** Health in hearts as the bar draws it, where a started half heart shows whole, with a German decimal comma. */
    static String hearts(double health) {
        int halves = (int) Math.ceil(health);
        return halves % 2 == 0 ? String.valueOf(halves / 2) : halves / 2 + ",5";
    }

    /** Every participant's recap remains available in the lobby; those who left early get theirs too. */
    private void store(String names) {
        var recaps = new LinkedHashMap<UUID, RoundRecap>();
        scores.forEach((id, score) -> recaps.put(id, new RoundRecap(api.server().group(), winners.containsKey(id), names,
                score.kills, score.deaths, score.bestStreak, score.coins, score.xp)));
        recaps.forEach((id, recap) -> recapWrites.run(id, () -> api.recaps().save(Map.of(id, recap))));
    }

    private void book() {
        var rows = new ArrayList<Row>();
        scores.forEach((id, score) -> {
            var delta = score.stats(winners.containsKey(id), !winnerNames().isEmpty());
            if (!delta.equals(Stats.EMPTY)) {
                rows.add(new Row(id, score.name, delta));
            }
        });
        Tasks.async(plugin, () -> rows.forEach(this::bookRow));
    }

    private void bookRow(Row row) {
        try {
            stats.add(row.player(), row.name(), row.delta());
        } catch (DatabaseException e) {
            plugin.getLogger().log(Level.WARNING, "Could not book the round's stats of " + row.name(), e);
        }
    }

    private Component button(String key, Player viewer, BooleanSupplier eligible, Consumer<Player> action) {
        return messages.get(key)
                .hoverEvent(HoverEvent.showText(messages.get(key + "-hover")))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience == viewer) {
                        Tasks.sync(plugin, () -> {
                            if (viewer.isOnline() && eligible.getAsBoolean()) {
                                Sounds.MENU_CLICK.play(viewer);
                                action.accept(viewer);
                            }
                        });
                    }
                }, BUTTON));
    }

    private void pay(Player player, int coins) {
        if (coins > 0) {
            Coins.award(plugin, player, coins);
            score(player).coins += coins;
        }
    }

    private Score score(Player player) {
        return scores.computeIfAbsent(player.getUniqueId(), id -> new Score(api.realName(player)));
    }

    static final class Score {
        final String name;
        int kills;
        int deaths;
        int streak;
        int bestStreak;
        int coins;
        int xp;
        boolean eliminated;
        final List<Component> notes = new ArrayList<>();

        Score(String name) {
            this.name = name;
        }

        Component compact(Messages messages, Component game, Component result) {
            return messages.get("summary.compact", Placeholder.component("game", game),
                    Placeholder.component("result", result), Formatter.number("coins", coins), Formatter.number("xp", xp));
        }

        List<Component> details(Messages messages) {
            var lines = new ArrayList<Component>();
            lines.add(messages.get("summary.kills", Formatter.number("kills", kills)));
            lines.add(messages.get("summary.deaths", Formatter.number("deaths", deaths)));
            if (bestStreak > 1) {
                lines.add(messages.get("summary.streak-best", Formatter.number("streak", bestStreak)));
            }
            lines.add(messages.get("summary.coins", Formatter.number("coins", coins)));
            lines.add(messages.get("summary.xp", Formatter.number("xp", xp)));
            lines.addAll(notes);
            return List.copyOf(lines);
        }

        /**
         * A draw costs nobody still standing a loss; a won round costs everyone else one, once. A winner who was out
         * before the end, as a team game's can be, books the win alone.
         */
        Stats stats(boolean won, boolean decided) {
            boolean lost = !won && (eliminated || decided);
            return new Stats(kills, deaths, won ? 1 : 0, lost ? 1 : 0, coins);
        }
    }
}
