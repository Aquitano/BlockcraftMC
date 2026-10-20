package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.duel.DuelPairRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.menu.VoteMenus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Duel {

    public static final int MAX_PARTICIPANTS = 2;
    private static final long RESPAWN_DELAY_TICKS = 5L;
    private static final int MIRROR_DRAWS = 2;

    private final Plugin plugin;
    private final GameConfig gameConfig;
    private final DuellConfig config;
    private final Messages messages;
    private final Spawns spawns;
    private final GameScoreboard scoreboard;
    private final GameItems items;
    private final DuelRatings ratings;
    private final Random random = new Random();
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown lobbyCountdown;
    private final LobbyHotbar lobbyHotbar;
    private final DuelVote votes = new DuelVote();
    private final Rematch rematch;
    private final DuelPairRepository pairs;
    private List<UUID> duellists = List.of();

    private DuelMode mode;
    private boolean pvp;
    private boolean frozen;
    private int mapNumber;
    private String mapName;
    private Countdown roundTimer;
    private int vsRound = 1;
    private boolean superJumpFinished;
    private final Map<UUID, Integer> checkpoints = new HashMap<>();
    private final Set<UUID> fallen = new HashSet<>();

    public Duel(Plugin plugin, GameConfig gameConfig, DuellConfig config, Messages messages, Spawns spawns,
                StatsRepository stats, StatsMenus statsMenus, GameItems items, DuelRatings ratings,
                DuelPairRepository pairs) {
        this.plugin = plugin;
        this.gameConfig = gameConfig;
        this.config = config;
        this.messages = messages;
        this.spawns = spawns;
        this.items = items;
        this.ratings = ratings;
        this.summary = new RoundSummary(plugin, messages,
                new RoundSummary.Rewards(config.killCoins(), config.winCoins()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.DUELL_JOIN), summary, MAX_PARTICIPANTS, gameConfig);
        this.scoreboard = new GameScoreboard(messages, round);
        this.lobbyCountdown = new LobbyCountdown(plugin, round, gameConfig, this::startRound);
        var voteMenus = new VoteMenus(this, messages);
        this.lobbyHotbar = new LobbyHotbar(plugin,
                List.of(new LobbyHotbar.Choice(MenuIcon.VOTE, messages.get("item.vote"), voteMenus::openModes)),
                statsMenus, lobbyCountdown);
        this.rematch = new Rematch(plugin, messages, config.rematchWindow());
        this.pairs = pairs;
    }

    public LobbyCountdown lobbyCountdown() {
        return lobbyCountdown;
    }

    public LobbyHotbar lobbyHotbar() {
        return lobbyHotbar;
    }

    public boolean isLobby() {
        return round.phase() == GamePhase.LOBBY;
    }

    public boolean isIngame() {
        return round.phase() == GamePhase.INGAME;
    }

    public boolean isPlaying(DuelMode mode) {
        return isIngame() && this.mode == mode;
    }

    public boolean isMode(DuelMode mode) {
        return this.mode == mode;
    }

    public boolean pvp() {
        return pvp;
    }

    public boolean frozen() {
        return frozen;
    }

    public DuelVote votes() {
        return votes;
    }

    public boolean isParticipant(Player player) {
        return round.isPlaying(player);
    }

    public Stream<Player> opponentsOf(Player player) {
        return participants().filter(other -> !other.equals(player));
    }

    /** Spawn of the current map for participant slot 1 or 2. */
    public Optional<Location> mapSpawn(int slot) {
        return spawns.mapSpawn(mode, mapNumber, slot);
    }

    public Optional<Location> lobbySpawn() {
        return spawns.spawn();
    }

    public void join(Player player) {
        (isLobby() ? lobbySpawn() : mapSpawn(1).or(this::lobbySpawn)).ifPresent(player::teleport);
        if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            lobbyHotbar.give(player);
            lobbyCountdown.start();
        } else {
            player.sendMessage(messages.get("join.spectator"));
        }
        ratings.load(player, this::refreshScoreboards);
        Tasks.sync(plugin, this::refreshScoreboards);
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    /** A player who vanishes in the lobby watches instead; the round, which hears it first, has unseated them already. */
    public void vanishChanged(Player player) {
        if (isLobby() && !isParticipant(player)) {
            votes.withdraw(player.getUniqueId());
        }
        refreshScoreboards();
    }

    public void nickChanged(Player player) {
        ratings.refreshTab(player);
        refreshScoreboards();
    }

    public void quit(Player player) {
        boolean left = round.leave(player);
        checkpoints.remove(player.getUniqueId());
        switch (round.phase()) {
            case INGAME -> {
                if (left && round.playerCount() <= 1) {
                    participants().forEach(winner -> rewardWinner(winner, player));
                    endRound();
                } else {
                    refreshScoreboards();
                }
            }
            case LOBBY -> {
                votes.withdraw(player.getUniqueId());
                refreshScoreboards();
            }
            case ENDING -> {
            }
        }
        ratings.forget(player);
    }

    /** A participant's vote, which replaces the one they cast before; only the voter hears where it went. */
    public void vote(Player voter, DuelVote.Choice choice) {
        if (!isLobby() || !isParticipant(voter)) {
            VoteMenu.closed(voter, messages);
            return;
        }
        var earlier = votes.cast(voter.getUniqueId(), choice);
        var map = choice.map().isPresent()
                ? Component.text(mapName(choice.mode(), choice.map().getAsInt()))
                : messages.get("vote.random-map");
        VoteMenu.confirm(voter, messages, messages.get("vote.option",
                        Placeholder.component("mode", modeName(choice.mode())), Placeholder.component("map", map)),
                earlier.filter(previous -> !previous.equals(choice)).isPresent());
        refreshScoreboards();
    }

    /** What /forcemap offers: the modes with a playable map. */
    public List<String> forcibleModes() {
        return forcible().map(DuelMode::configKey).toList();
    }

    /** /forcemap: the mode is settled, its map still comes from the votes; empty for a mode that cannot be forced. */
    public Optional<Component> forceMode(String key) {
        return forcible().filter(mode -> mode.configKey().equalsIgnoreCase(key)).findFirst().map(mode -> {
            votes.force(mode);
            refreshScoreboards();
            return modeName(mode);
        });
    }

    private Stream<DuelMode> forcible() {
        return Arrays.stream(DuelMode.values()).filter(mode -> !playableMaps(mode).isEmpty());
    }

    /** The maps of {@code mode} with a spawn for every slot, by number. */
    public List<Integer> playableMaps(DuelMode mode) {
        return IntStream.rangeClosed(1, config.maps(mode).size())
                .filter(map -> spawns.hasMapSpawns(mode, map, MAX_PARTICIPANTS))
                .boxed()
                .toList();
    }

    public String mapName(DuelMode mode, int map) {
        return config.maps(mode).get(map - 1);
    }

    public void handleDeath(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        if (!isIngame() || !isParticipant(victim)) {
            return;
        }
        if (mode == DuelMode.SUPERJUMP) {
            fell(victim);
            round.respawn(victim, RESPAWN_DELAY_TICKS);
            return;
        }
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        if (mode == DuelMode.SG) {
            handleSgDeath(victim, death.killer());
            return;
        }
        death.killer().ifPresentOrElse(killer -> handleVsKill(victim, killer), () -> {
            summary.death(victim);
            round.respawn(victim, RESPAWN_DELAY_TICKS);
        });
    }

    /** The next round's title goes to the killer alone; the victim reads who beat them instead. */
    private void handleVsKill(Player victim, Player killer) {
        vsRound++;
        summary.awardKill(killer, victim, Defeat.POINT);
        summary.death(victim);
        refreshScoreboards();
        round.respawn(victim, RESPAWN_DELAY_TICKS);
        if (vsRound > config.vsKillsToEnd()) {
            var winner = summary.kills(victim) > summary.kills(killer) ? victim : killer;
            rewardWinner(winner);
            endRound();
            return;
        }
        var title = Title.title(messages.get("title.vs-round", Formatter.number("round", vsRound)), messages.get("title.start"));
        BlockcraftApi.get().notifications().important(killer, title);
    }

    /** Whoever dies loses the duel, whether the opponent or the map killed them. */
    private void handleSgDeath(Player victim, Optional<Player> killer) {
        summary.death(victim);
        killer.ifPresent(slayer -> summary.awardKill(slayer, victim, Defeat.OUT));
        opponentsOf(victim).forEach(this::rewardWinner);
        endRound();
        round.respawn(victim, RESPAWN_DELAY_TICKS);
    }

    /** Returns true if this checkpoint is new for the player. */
    public boolean reachCheckpoint(Player player, int checkpoint) {
        if (checkpoints.getOrDefault(player.getUniqueId(), 0) >= checkpoint) {
            return false;
        }
        checkpoints.put(player.getUniqueId(), checkpoint);
        refreshScoreboards();
        Bukkit.broadcast(messages.get("superjump.checkpoint", Placeholder.unparsed("player", player.getName()),
                Formatter.number("checkpoint", checkpoint)));
        player.setRespawnLocation(player.getLocation(), true);
        Sounds.NOTIFY.play(player);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 5);
        return true;
    }

    public void finishSuperJump(Player winner) {
        if (superJumpFinished) {
            return;
        }
        superJumpFinished = true;
        if (!fallen.contains(winner.getUniqueId())) {
            summary.moment(winner, "duell", "trittsicher");
        }
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(messages.get("superjump.goal", Placeholder.unparsed("player", winner.getName())));
        rewardWinner(winner);
        Bukkit.broadcast(Component.empty());
        winner.getWorld().spawnParticle(Particle.EXPLOSION, winner.getLocation(), 5);
        winner.setRespawnLocation(winner.getLocation(), true);
        startRestart();
    }

    public void fell(Player player) {
        fallen.add(player.getUniqueId());
    }

    public void refreshScoreboards() {
        if (round.phase() == GamePhase.ENDING) {
            var winners = summary.winnerNames();
            Bukkit.getOnlinePlayers().forEach(viewer -> scoreboard.showEnd(viewer, winners));
            return;
        }
        var board = new GameScoreboard.Board(modeLine(), mapLine(),
                roundTimer == null ? config.countdowns().round() : roundTimer.remaining());
        participants().forEach(participant -> scoreboard.showDuellist(participant, board, ratings.placeholders(participant),
                opponentsOf(participant).filter(participant::canSee).map(Player::getName).toList(),
                isPlaying(DuelMode.VS) ? OptionalInt.of(summary.kills(participant)) : OptionalInt.empty()));
        Bukkit.getOnlinePlayers().stream().filter(player -> !isParticipant(player)).forEach(viewer ->
                scoreboard.showSpectator(viewer, board, participants().filter(viewer::canSee).map(this::duellistLine).toList()));
    }

    private Component modeLine() {
        if (mode != null) {
            return modeName(mode);
        }
        if (votes.forced().isPresent()) {
            return modeName(votes.forced().get());
        }
        return votes.leader()
                .map(leader -> messages.get("scoreboard.mode-leading", Placeholder.component("mode", modeName(leader.mode()))))
                .orElseGet(() -> messages.get("scoreboard.mode-voting"));
    }

    /** Before the start, the map the vote leans to, if it leans to one. */
    private Component mapLine() {
        var name = mode != null ? Optional.ofNullable(mapName) : votes.leader().flatMap(leader ->
                leader.map().stream().mapToObj(map -> mapName(leader.mode(), map)).findFirst());
        return name.<Component>map(Component::text).orElseGet(() -> messages.get("scoreboard.map-random"));
    }

    private Component duellistLine(Player duellist) {
        var name = Placeholder.unparsed("player", duellist.getName());
        if (isPlaying(DuelMode.VS)) {
            return messages.get("scoreboard.spectator-kills", name, Formatter.number("kills", summary.kills(duellist)));
        }
        if (isPlaying(DuelMode.SUPERJUMP)) {
            return messages.get("scoreboard.spectator-checkpoint", name,
                    Formatter.number("checkpoint", checkpoints.getOrDefault(duellist.getUniqueId(), 0)));
        }
        return messages.get("scoreboard.spectator-player", name);
    }

    private Component modeName(DuelMode mode) {
        return messages.get("mode." + mode.configKey());
    }

    private Stream<Player> participants() {
        return round.players().stream();
    }

    private void startRound() {
        duellists = round.players().stream().map(Player::getUniqueId).toList();
        round.enter(GamePhase.INGAME);
        recognizeOpponents();
        var playable = new EnumMap<DuelMode, List<Integer>>(DuelMode.class);
        for (var candidate : DuelMode.values()) {
            playable.put(candidate, playableMaps(candidate));
        }
        var outcome = votes.tally(playable, random);
        mode = outcome.mode();
        mapNumber = outcome.map();
        mapName = config.maps(mode).isEmpty() ? null : mapName(mode, mapNumber);
        Bukkit.broadcast(messages.get("vote.result", Placeholder.component("mode", modeName(mode)),
                Placeholder.component("map", mapName == null ? messages.get("vote.random-map") : Component.text(mapName))));
        int slot = 1;
        for (var player : round.players()) {
            PlayerReset.reset(player, GameMode.SURVIVAL);
            teleportToMapSpawn(player, slot++);
            switch (mode) {
                case VS -> {
                    items.giveVsKit(player);
                    config.combat().apply(player);
                }
                case SG -> config.combat().apply(player);
                case SUPERJUMP -> player.setRespawnLocation(player.getLocation(), true);
            }
        }
        refreshScoreboards();
        freeze();
    }

    private void teleportToMapSpawn(Player player, int slot) {
        mapSpawn(slot).ifPresentOrElse(player::teleport,
                () -> plugin.getLogger().warning("No spawn set for " + mode.setupKey() + " map " + mapNumber + " player " + slot));
    }

    private void freeze() {
        frozen = true;
        var introduction = messages.get("title.introduction",
                Placeholder.component("mode", modeName(mode)), Placeholder.component("map", mapLine()));
        round.timer(RoundStart.freeze(plugin, config.countdowns().freeze(), round::players, introduction, () -> {
            frozen = false;
            pvp = mode != DuelMode.SUPERJUMP;
            startRoundTimer();
        }));
    }

    private void startRoundTimer() {
        roundTimer = round.clock(new TimerBar(messages, "timer.round", config.countdowns().round()), remaining -> {
            if (round.clockOnSidebar()) {
                refreshScoreboards();
            }
        }, this::endByTimeout);
    }

    /** The clock decides: VS by kills, SuperJump by the furthest checkpoint, SG is a draw. */
    private void endByTimeout() {
        var winner = switch (mode) {
            case VS -> leaderBy(summary::kills);
            case SUPERJUMP -> leaderBy(player -> checkpoints.getOrDefault(player.getUniqueId(), 0));
            case SG -> Optional.<Player>empty();
        };
        winner.ifPresentOrElse(this::rewardWinner, () -> Bukkit.broadcast(messages.pick("round.draw")));
        endRound();
    }

    private Optional<Player> leaderBy(ToIntFunction<Player> score) {
        var players = round.players();
        if (players.size() < 2) {
            return players.stream().findFirst();
        }
        int first = score.applyAsInt(players.get(0));
        int second = score.applyAsInt(players.get(1));
        return first == second ? Optional.empty() : Optional.of(first > second ? players.get(0) : players.get(1));
    }

    private void endRound() {
        pvp = false;
        Bukkit.broadcast(messages.get("round.ended"));
        startRestart();
    }

    /** Duellists who are both still here are offered a rematch. */
    private void startRestart() {
        frozen = false;
        if (round.end(Math.max(gameConfig.restartSeconds(), Math.toIntExact(config.rematchWindow().toSeconds()) + 2))) {
            rememberResult();
            refreshScoreboards();
            var duellists = round.players();
            if (duellists.size() == MAX_PARTICIPANTS) {
                rematch.offer(duellists.get(0), duellists.get(1));
                rematch.offer(duellists.get(1), duellists.get(0));
            }
        }
    }

    private void recognizeOpponents() {
        if (duellists.size() != MAX_PARTICIPANTS) {
            return;
        }
        var first = duellists.getFirst();
        var second = duellists.getLast();
        Tasks.async(plugin, () -> pairs.met(first, second), familiar -> {
            var one = Bukkit.getPlayer(first);
            var two = Bukkit.getPlayer(second);
            if (familiar && isIngame() && one != null && two != null && isParticipant(one) && isParticipant(two)
                    && one.canSee(two) && two.canSee(one)) {
                one.sendMessage(messages.get("mirror.familiar", Placeholder.unparsed("opponent", two.getName())));
                two.sendMessage(messages.get("mirror.familiar", Placeholder.unparsed("opponent", one.getName())));
            }
        });
    }

    private void rememberResult() {
        if (duellists.size() != MAX_PARTICIPANTS) {
            return;
        }
        boolean draw = summary.winnerNames().isEmpty();
        var first = duellists.getFirst();
        var second = duellists.getLast();
        String tag = ChatTagChoice.of(ChatTag.MIRROR).item();
        Tasks.async(plugin, () -> pairs.finish(first, second, draw, MIRROR_DRAWS, tag), unlocked -> {
            if (unlocked) {
                duellists.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(player ->
                        player.sendMessage(messages.get("mirror.inspection")));
            }
        });
    }

    /** The round books the loss of everyone the winner beat; the rating moves here. */
    private void rewardWinner(Player winner) {
        awardWin(winner);
        opponentsOf(winner).findFirst().ifPresent(loser -> ratings.settle(winner, loser, this::refreshScoreboards));
    }

    /**
     * A quitter has already left the participants, so the round names them as the loser itself. Their leaving earns
     * the winner no moment, so two accounts cannot farm it.
     */
    private void rewardWinner(Player winner, Player quitter) {
        summary.awardWin(winner);
        ratings.settle(winner, quitter, this::refreshScoreboards);
    }

    private void awardWin(Player winner) {
        summary.awardWin(winner);
        if (mode != DuelMode.SUPERJUMP && summary.undamaged(winner)) {
            summary.moment(winner, "duell", "makellos");
        }
    }

    public void shutdown() {
        lobbyCountdown.cancel();
        round.stop();
    }
}
