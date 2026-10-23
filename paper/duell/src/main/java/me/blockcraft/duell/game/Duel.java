package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.game.Arena;
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
import me.blockcraft.api.task.SerialTasks;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.duel.DuelPairRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import me.blockcraft.duell.menu.VoteMenus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Duel {
    public static final int MAX_PARTICIPANTS = 2;
    private static final int MIRROR_DRAWS = 2;
    private static final int REMATCH_SLOT = 1;
    private final Plugin plugin;
    private final DuellConfig config;
    private final Messages messages;
    private final Spawns spawns;
    private final DuelGames games;
    private final GameScoreboard scoreboard;
    private final DuelRatings ratings;
    private final StatsRepository stats;
    private final Random random = new Random();
    private final SerialTasks<UUID> recapWrites;
    private RoundSummary summary;
    private final Round round;
    private final LobbyCountdown lobbyCountdown;
    private final LobbyHotbar lobbyHotbar;
    private final NamespacedKey rematchKey;
    private Rematch rematch;
    private final DuelVote votes;
    private final DuelPairRepository pairs;
    private List<UUID> duellists = List.of();
    private DuelSeries series;
    private DuelGame current;
    private DuelRating activeRating = DuelRating.UNRATED;
    private boolean gameActive;
    private boolean frozen;
    private DuelMode mode;
    private String mapName;
    private Countdown roundTimer;
    private boolean stopped;

    public Duel(Plugin plugin, GameConfig gameConfig, DuellConfig config, Messages messages, Spawns spawns,
                DuelGames games, StatsRepository stats, StatsMenus statsMenus, DuelRatings ratings, DuelPairRepository pairs) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.spawns = spawns;
        this.games = games;
        this.votes = new DuelVote(games.mapPresets());
        this.ratings = ratings;
        this.stats = stats;
        this.recapWrites = new SerialTasks<>(plugin);
        this.summary = nextSummary();
        this.round = new Round(plugin, new Arena(messages, Sounds.DUELL_JOIN), summary, MAX_PARTICIPANTS, gameConfig);
        this.scoreboard = new GameScoreboard(messages, round);
        this.lobbyCountdown = new LobbyCountdown(plugin, round, gameConfig, this::startRound);
        var voteMenus = new VoteMenus(this, messages);
        this.rematchKey = new NamespacedKey(plugin, "rematch-series");
        this.lobbyHotbar = new LobbyHotbar(plugin,
                List.of(new LobbyHotbar.Choice(MenuIcon.VOTE, messages.get("item.vote"), this::isLobby, voteMenus::openModes),
                        new LobbyHotbar.Choice(MenuIcon.DUELL, messages.get("item.rematch"),
                                () -> round.phase() == GamePhase.ENDING && rematch != null, this::requestRematchFromItem)),
                statsMenus, lobbyCountdown);
        this.pairs = pairs;
        ratings.displayWhen(() -> !isLobby() && activeRating == DuelRating.RATED);
    }

    private RoundSummary nextSummary() {
        return new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.killCoins(), config.winCoins()), stats, recapWrites);
    }

    public LobbyCountdown lobbyCountdown() { return lobbyCountdown; }
    public LobbyHotbar lobbyHotbar() { return lobbyHotbar; }
    public boolean isLobby() { return round.phase() == GamePhase.LOBBY; }
    public boolean isIngame() { return round.phase() == GamePhase.INGAME; }
    public boolean isPlaying(DuelMode mode) { return isIngame() && gameActive && this.mode == mode; }
    public boolean isMode(DuelMode mode) { return this.mode == mode; }
    public boolean pvp() { return isIngame() && gameActive && !frozen && mode != DuelMode.SUPERJUMP; }
    public boolean frozen() { return frozen; }
    public DuelVote votes() { return votes; }
    public boolean isParticipant(Player player) { return round.isPlaying(player); }
    public Stream<Player> opponentsOf(Player player) { return participants().filter(other -> !other.equals(player)); }
    public Optional<Location> lobbySpawn() { return spawns.spawn(); }
    public DuelSeries.Format formatChoice(UUID player) { return votes.formatChoice(player); }
    public DuelSeries.Format selectedFormat() {
        return !isLobby() && series != null ? series.schedule().size() == 3 ? DuelSeries.Format.TOUR : DuelSeries.Format.SINGLE
                : votes.selectedFormat(round.players().stream().map(Player::getUniqueId).toList());
    }
    public DuelRating ratingChoice(UUID player) { return votes.ratingChoice(player); }
    public boolean ratedEnabled() { return config.ratedEnabled(); }
    public DuelRating selectedRating() {
        return isLobby() ? votes.selectedRating(round.players().stream().map(Player::getUniqueId).toList(), ratedEnabled()) : activeRating;
    }

    public void chooseRating(Player player, DuelRating rating) {
        if (!isLobby() || !isParticipant(player) || rating == DuelRating.RATED && !ratedEnabled()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var previous = votes.ratingChoice(player.getUniqueId());
        votes.chooseRating(player.getUniqueId(), rating);
        player.sendMessage(messages.get("rating.chosen", Placeholder.component("rating", ratingName(rating))));
        if (rating == DuelRating.RATED && selectedRating() != DuelRating.RATED) {
            player.sendMessage(messages.get("rating.waiting"));
            if (previous != rating) {
                opponentsOf(player).forEach(opponent -> opponent.sendMessage(messages.get("rating.request",
                        Placeholder.unparsed("player", player.getName()))));
            }
        }
        refreshScoreboards();
    }

    private Component ratingName(DuelRating rating) {
        return messages.get(rating == DuelRating.RATED ? "rating.rated" : "rating.unrated");
    }

    public void chooseFormat(Player player, DuelSeries.Format format) {
        if (!isLobby() || !isParticipant(player)) {
            VoteMenu.closed(player, messages);
            return;
        }
        var previous = votes.formatChoice(player.getUniqueId());
        votes.chooseFormat(player.getUniqueId(), format);
        player.sendMessage(messages.get("tour.format-chosen", Placeholder.component("format",
                messages.get(format == DuelSeries.Format.TOUR ? "tour.format-tour" : "tour.format-single"))));
        if (format == DuelSeries.Format.TOUR && selectedFormat() != DuelSeries.Format.TOUR) {
            player.sendMessage(messages.get("tour.format-waiting"));
            if (previous != format) {
                opponentsOf(player).forEach(opponent -> opponent.sendMessage(messages.get("tour.format-request",
                        Placeholder.unparsed("player", player.getName()))));
            }
        }
        refreshScoreboards();
    }

    public boolean rushParticipant(Player player) {
        return isPlaying(DuelMode.MLGRUSH) && isParticipant(player) && games.rushOwns(player.getLocation());
    }

    public void join(Player player) {
        lobbySpawn().ifPresent(player::teleport);
        if (round.join(player)) {
            prepareLobby(player);
            lobbyCountdown.start();
        } else {
            if (current != null) {
                current.spectate(player);
            }
            player.sendMessage(messages.get("join.spectator"));
        }
        Tasks.sync(plugin, this::refreshScoreboards);
    }

    private void prepareLobby(Player player) {
        if (player.isDead()) {
            player.spigot().respawn();
        }
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.playerListName(null);
        lobbySpawn().ifPresent(location -> {
            player.teleport(location);
            player.setRespawnLocation(location, true);
        });
        lobbyHotbar.give(player);
    }

    private void prepareEnding(Player player) {
        if (player.isDead()) {
            return;
        }
        PlayerReset.reset(player, GameMode.ADVENTURE);
        lobbySpawn().ifPresent(location -> {
            player.teleport(location);
            player.setRespawnLocation(location, true);
        });
        lobbyHotbar.give(player);
        var item = player.getInventory().getItem(REMATCH_SLOT);
        if (rematch != null && item != null) {
            item.editMeta(meta -> meta.getPersistentDataContainer().set(rematchKey, PersistentDataType.STRING, rematch.identity()));
            player.getInventory().setItem(REMATCH_SLOT, item);
        }
    }

    private void requestRematchFromItem(Player player) {
        var identity = player.getInventory().getItemInMainHand().getPersistentDataContainer()
                .get(rematchKey, PersistentDataType.STRING);
        if (rematch != null) {
            rematch.request(player, identity);
        }
    }

    public void follow(Player spectator, UUID friend) { round.follow(spectator, friend); }

    public void vanishChanged(Player player, boolean vanished) {
        if (vanished && isParticipant(player) && !isLobby()) {
            quit(player);
            round.watch(player);
        }
        if (isLobby()) {
            if (vanished) {
                votes.withdraw(player.getUniqueId());
            }
            Tasks.sync(plugin, this::updateLobby);
        }
        refreshScoreboards();
    }

    public void nickChanged(Player player) {
        if (selectedRating() == DuelRating.RATED) {
            ratings.refreshTab(player);
        }
        refreshScoreboards();
    }

    public void quit(Player player) {
        boolean left = round.leave(player);
        votes.withdraw(player.getUniqueId());
        if (left && isIngame() && series != null) {
            closeGame();
            participants().findFirst().ifPresent(winner -> rewardWinner(winner, player));
            endRound();
        } else if (left && round.phase() == GamePhase.ENDING) {
            var completed = series;
            Tasks.sync(plugin, () -> {
                if (series == completed && round.phase() == GamePhase.ENDING) {
                    reopen(false);
                }
            });
        } else if (isLobby()) {
            Tasks.sync(plugin, this::updateLobby);
        }
        ratings.forget(player);
        refreshScoreboards();
    }

    private void updateLobby() {
        if (!isLobby() || stopped) {
            return;
        }
        if (round.playerCount() < MAX_PARTICIPANTS) {
            lobbyCountdown.cancel();
            round.spectators().stream().filter(player -> !BlockcraftApi.get().isVanished(player))
                    .limit(MAX_PARTICIPANTS - round.playerCount()).forEach(this::join);
        }
        lobbyCountdown.start();
        refreshScoreboards();
    }

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

    public void clearModeVote(Player player) {
        if (!isLobby() || !isParticipant(player)) {
            VoteMenu.closed(player, messages);
            return;
        }
        votes.clearChoice(player.getUniqueId());
        player.sendMessage(messages.get("vote.cleared"));
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

    public List<Integer> playableMaps(DuelMode mode) {
        if (mode == DuelMode.SUMO) { return List.of(1); }
        if (mode == DuelMode.MLGRUSH) { return games.rushMaps(); }
        return IntStream.rangeClosed(1, config.maps(mode).size())
                .filter(map -> spawns.hasMapSpawns(mode, map, MAX_PARTICIPANTS)).boxed().toList();
    }

    public String mapName(DuelMode mode, int map) {
        return mode == DuelMode.SUMO ? messages.raw("sumo.map-name")
                : mode == DuelMode.MLGRUSH ? games.rushMapName(map) : config.maps(mode).get(map - 1);
    }

    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (current != null && isParticipant(player)) {
            current.respawn(event);
        } else {
            lobbySpawn().ifPresent(event::setRespawnLocation);
            var completed = summary;
            Tasks.sync(plugin, () -> {
                if (!stopped && current == null && !isIngame() && summary == completed && player.isOnline() && !player.isDead() && isParticipant(player)) {
                    if (isLobby()) { prepareLobby(player); } else { prepareEnding(player); }
                }
            });
        }
    }

    public void refreshScoreboards() {
        if (stopped) { return; }
        if (round.phase() == GamePhase.ENDING) {
            var winners = summary.winnerNames();
            Bukkit.getOnlinePlayers().forEach(viewer -> scoreboard.showEnd(viewer, winners));
            return;
        }
        var board = new GameScoreboard.Board(modeLine(), mapLine(),
                roundTimer == null ? config.countdowns().round() : roundTimer.remaining(), tourProgress());
        participants().forEach(participant -> scoreboard.showDuellist(participant, board, ratingName(selectedRating()),
                opponentsOf(participant).filter(participant::canSee).map(Player::getName).toList(),
                current != null && (mode == DuelMode.VS || mode == DuelMode.SUMO || mode == DuelMode.MLGRUSH)
                        ? OptionalInt.of(current.progress(participant.getUniqueId())) : OptionalInt.empty(),
                isMode(DuelMode.SUMO) || isMode(DuelMode.MLGRUSH), tourScore(participant)));
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

    private Component duellistLine(Player player) {
        String key = isMode(DuelMode.SUPERJUMP) ? "scoreboard.spectator-checkpoint"
                : isMode(DuelMode.VS) ? "scoreboard.spectator-kills"
                : isMode(DuelMode.SUMO) || isMode(DuelMode.MLGRUSH) ? "scoreboard.spectator-points"
                : "scoreboard.spectator-player";
        int progress = current == null ? 0 : current.progress(player.getUniqueId());
        return messages.get(key, Placeholder.unparsed("player", player.getName()),
                Formatter.number("points", progress), Formatter.number("kills", progress), Formatter.number("checkpoint", progress));
    }

    private Component modeName(DuelMode mode) {
        return messages.get("mode." + mode.configKey());
    }

    private Stream<Player> participants() {
        return round.players().stream();
    }

    private void startRound() {
        startRound(config.countdowns().freeze());
    }

    private void startRound(int freezeSeconds) {
        var players = round.players();
        if (!isLobby() || stopped || players.size() != MAX_PARTICIPANTS
                || players.stream().anyMatch(BlockcraftApi.get()::isVanished)) {
            return;
        }
        lobbyCountdown.cancel();
        duellists = players.stream().map(Player::getUniqueId).toList();
        var playable = new EnumMap<DuelMode, List<Integer>>(DuelMode.class);
        for (var candidate : DuelMode.values()) { playable.put(candidate, playableMaps(candidate)); }
        var requested = selectedFormat();
        var schedule = DuelSeries.plan(requested, votes, playable, random);
        activeRating = selectedRating();
        series = new DuelSeries(duellists, schedule);
        round.enter(GamePhase.INGAME);
        if (activeRating == DuelRating.RATED) { players.forEach(player -> ratings.load(player, this::refreshScoreboards)); }
        recognizeOpponents();
        if (requested == DuelSeries.Format.TOUR && schedule.size() == 1) {
            Bukkit.broadcast(messages.get("tour.fallback"));
        } else if (isTour()) {
            var modes = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")),
                    schedule.stream().map(game -> modeName(game.mode())).toList());
            Bukkit.broadcast(messages.get("tour.schedule", Placeholder.component("modes", modes)));
        }
        startGame(freezeSeconds);
    }

    private void startGame(int freezeSeconds) {
        if (!isIngame() || series == null || series.finished() || round.playerCount() != MAX_PARTICIPANTS || stopped) {
            return;
        }
        closeGame();
        var started = series;
        int index = started.gameIndex();
        var outcome = started.current().orElseThrow();
        mode = outcome.mode();
        mapName = mapName(mode, outcome.map());
        summary.nextGame();
        frozen = true;
        gameActive = true;
        var sides = new ArrayList<>(duellists);
        if (index % MAX_PARTICIPANTS != 0) { Collections.reverse(sides); }
        round.players().stream().filter(Player::isDead).forEach(player -> player.spigot().respawn());
        current = games.open(outcome, sides, summary, new DuelGames.Events() {
            @Override
            public void changed() {
                if (series == started && series.gameIndex() == index && gameActive) { refreshScoreboards(); }
            }
            @Override
            public void completed(Optional<UUID> winner) { finishGame(started, index, winner); }
        });
        round.spectators().forEach(current::spectate);
        Bukkit.broadcast(messages.get("vote.result", Placeholder.component("mode", modeName(mode)),
                Placeholder.component("map", Component.text(mapName))));
        Runnable activate = () -> {
            if (!stopped && isIngame() && series == started && series.gameIndex() == index && gameActive) {
                frozen = false;
                current.activate();
                startRoundTimer(started, index);
            }
        };
        refreshScoreboards();
        if (freezeSeconds == 0) {
            RoundStart.go(round.players());
            activate.run();
        } else {
            round.timer(RoundStart.freeze(plugin, freezeSeconds, round::players,
                    messages.get("title.introduction", Placeholder.component("mode", modeName(mode)),
                            Placeholder.component("map", mapLine())), activate));
        }
    }

    private void startRoundTimer(DuelSeries started, int index) {
        roundTimer = round.clock(new TimerBar(messages, "timer.round", config.countdowns().round()), remaining -> {
            if (round.clockOnSidebar()) { refreshScoreboards(); }
        }, () -> {
            if (series == started && series.gameIndex() == index && gameActive && current != null) {
                finishGame(started, index, current.timeoutWinner());
            }
        });
    }

    private void closeGame() {
        gameActive = false;
        frozen = false;
        if (current != null) {
            current.close();
            current = null;
        }
        round.stop();
        roundTimer = null;
    }

    private boolean isTour() {
        return series != null && series.schedule().size() == 3;
    }

    private List<Component> tourProgress() {
        if (isTour()) {
            return List.of(messages.get("scoreboard.tour-progress",
                    Formatter.number("game", Math.min(series.gameIndex() + 1, series.schedule().size())),
                    Formatter.number("total", series.schedule().size())));
        }
        return isLobby() && selectedFormat() == DuelSeries.Format.TOUR
                ? List.of(messages.get("tour.format-tour")) : List.of();
    }

    private Optional<Component> tourScore(Player player) {
        return isTour() ? opponentsOf(player).findFirst().map(opponent -> messages.get("scoreboard.tour-score",
                Formatter.number("wins", series.wins(player.getUniqueId())),
                Formatter.number("opponent-wins", series.wins(opponent.getUniqueId())))) : Optional.empty();
    }

    private void finishGame(DuelSeries completed, int index, Optional<UUID> winner) {
        if (stopped || !gameActive || series != completed || !isIngame() || !series.complete(completed, index, winner)) {
            return;
        }
        winner.map(Bukkit::getPlayer).filter(player -> mode == DuelMode.VS || mode == DuelMode.SG)
                .filter(summary::undamaged).ifPresent(player -> summary.moment(player, "duell", "makellos"));
        closeGame();
        frozen = true;
        if (isTour()) {
            var result = winner.map(Bukkit::getPlayer).map(player -> messages.get("tour.game-win",
                            Placeholder.unparsed("player", player.getName()), Placeholder.component("mode", modeName(mode))))
                    .orElseGet(() -> messages.get("tour.game-draw", Placeholder.component("mode", modeName(mode))));
            round.players().forEach(player -> summary.note(player, result));
            Bukkit.broadcast(result);
            broadcastTourScore();
        }
        if (series.finished()) {
            series.winner().map(Bukkit::getPlayer).ifPresent(this::rewardWinner);
            if (isTour()) {
                round.players().forEach(player -> summary.note(player, messages.get("tour.result-note",
                        Formatter.number("score", series.wins(player.getUniqueId())), Formatter.number("games", series.schedule().size()))));
            }
            endRound();
            return;
        }
        int nextIndex = series.gameIndex();
        var next = series.current().orElseThrow();
        Bukkit.broadcast(messages.get("tour.next", Formatter.number("game", nextIndex + 1),
                Formatter.number("total", series.schedule().size()), Placeholder.component("mode", modeName(next.mode())),
                Formatter.number("seconds", config.tourIntermission().toSeconds())));
        refreshScoreboards();
        Tasks.sync(plugin, () -> {
            if (!stopped && series == completed && isIngame() && !gameActive && series.gameIndex() == nextIndex) {
                startGame(Math.toIntExact(config.tourIntermission().toSeconds()));
            }
        });
    }

    private void endRound() {
        closeGame();
        Bukkit.broadcast(messages.get("round.ended"));
        var completed = series;
        var offered = new Rematch(plugin, messages, config.rematchWindow(),
                (player, opponent) -> validRematch(completed, player, opponent), (player, opponent) -> reopen(true));
        rematch = offered;
        if (round.endInPlace(offered::request)) {
            Tasks.sync(plugin, () -> {
                if (!stopped && series == completed && round.phase() == GamePhase.ENDING) {
                    round.players().stream().filter(Player::isDead).forEach(player -> player.spigot().respawn());
                }
            });
            rememberResult();
            var players = round.players();
            if (players.size() == MAX_PARTICIPANTS) {
                offered.offer(players.getFirst(), players.getLast());
                offered.offer(players.getLast(), players.getFirst());
            } else {
                Tasks.sync(plugin, () -> {
                    if (!stopped && series == completed && round.phase() == GamePhase.ENDING) { reopen(false); }
                });
            }
            players.forEach(this::prepareEnding);
            refreshScoreboards();
        }
    }

    private boolean validRematch(DuelSeries completed, Player player, Player opponent) {
        return !stopped && completed != null && series == completed && round.phase() == GamePhase.ENDING
                && !player.equals(opponent) && player.isOnline() && opponent.isOnline()
                && round.playerCount() == MAX_PARTICIPANTS && isParticipant(player) && isParticipant(opponent)
                && completed.participants().contains(player.getUniqueId()) && completed.participants().contains(opponent.getUniqueId())
                && !BlockcraftApi.get().isVanished(player) && !BlockcraftApi.get().isVanished(opponent);
    }

    private void reopen(boolean samePair) {
        if (stopped || round.phase() != GamePhase.ENDING) { return; }
        closeGame();
        if (!samePair) {
            duellists.forEach(votes::withdraw);
        }
        votes.clearForced();
        votes.clearRatings();
        series = null;
        rematch = null;
        mode = null;
        mapName = null;
        activeRating = DuelRating.UNRATED;
        summary = nextSummary();
        round.reopen(summary);
        round.players().forEach(this::prepareLobby);
        if (samePair) {
            startRound(0);
        } else {
            updateLobby();
        }
        refreshScoreboards();
    }

    private void broadcastTourScore() {
        if (duellists.size() != MAX_PARTICIPANTS) {
            return;
        }
        var first = Bukkit.getPlayer(duellists.getFirst());
        var second = Bukkit.getPlayer(duellists.getLast());
        if (first != null && second != null) {
            Bukkit.broadcast(messages.get("tour.score", Placeholder.unparsed("first", first.getName()),
                    Formatter.number("first-score", series.wins(first.getUniqueId())),
                    Placeholder.unparsed("second", second.getName()),
                    Formatter.number("second-score", series.wins(second.getUniqueId()))));
        }
    }

    private void recognizeOpponents() {
        if (duellists.size() != MAX_PARTICIPANTS) {
            return;
        }
        var first = duellists.getFirst();
        var second = duellists.getLast();
        var started = series;
        Tasks.async(plugin, () -> pairs.met(first, second), familiar -> {
            var one = Bukkit.getPlayer(first);
            var two = Bukkit.getPlayer(second);
            if (familiar && series == started && isIngame() && one != null && two != null && isParticipant(one) && isParticipant(two)
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
        var completedPlayers = List.copyOf(duellists);
        String tag = ChatTagChoice.of(ChatTag.MIRROR).item();
        Tasks.async(plugin, () -> pairs.finish(first, second, draw, MIRROR_DRAWS, tag), unlocked -> {
            if (unlocked) {
                completedPlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(player ->
                        player.sendMessage(messages.get("mirror.inspection")));
            }
        });
    }

    private void rewardWinner(Player winner) {
        summary.awardWin(winner);
        opponentsOf(winner).findFirst().ifPresent(loser -> ratings.settle(activeRating, winner, loser, this::refreshScoreboards));
    }

    private void rewardWinner(Player winner, Player quitter) {
        summary.awardWin(winner);
        ratings.settle(activeRating, winner, quitter, this::refreshScoreboards);
    }

    public void shutdown() {
        stopped = true;
        lobbyCountdown.cancel();
        closeGame();
    }
}
