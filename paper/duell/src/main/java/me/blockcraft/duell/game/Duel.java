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
import me.blockcraft.duell.arena.SumoArena;
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
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

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
    private final SumoArena sumoArena;
    private SumoMatch sumoMatch;
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
    private DuelSeries series;
    private final Map<UUID, Integer> gameKills = new HashMap<>();
    private boolean gameActive;
    private int gameIdentity;

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

    public Duel(Plugin plugin, GameConfig gameConfig, DuellConfig config, Messages messages, Spawns spawns, SumoArena sumoArena,
                StatsRepository stats, StatsMenus statsMenus, GameItems items, DuelRatings ratings,
                DuelPairRepository pairs) {
        this.plugin = plugin;
        this.gameConfig = gameConfig;
        this.config = config;
        this.messages = messages;
        this.spawns = spawns;
        this.sumoArena = sumoArena;
        this.sumoMatch = new SumoMatch(config.sumo().pointsToWin());
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
        return isIngame() && gameActive && this.mode == mode;
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

    public DuelSeries.Format formatChoice(UUID player) {
        return votes.formatChoice(player);
    }

    public DuelSeries.Format selectedFormat() {
        return votes.selectedFormat(round.players().stream().map(Player::getUniqueId).toList());
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

    public boolean isParticipant(Player player) {
        return round.isPlaying(player);
    }

    public Stream<Player> opponentsOf(Player player) {
        return participants().filter(other -> !other.equals(player));
    }

    /** Spawn of the current map for participant slot 1 or 2. */
    public Optional<Location> mapSpawn(int slot) {
        return mode == DuelMode.SUMO ? Optional.of(sumoArena.spawn(slot)) : spawns.mapSpawn(mode, mapNumber, slot);
    }

    public Optional<Location> participantSpawn(Player player) {
        int slot = duellists.indexOf(player.getUniqueId());
        int offset = series == null ? 0 : series.gameIndex() % MAX_PARTICIPANTS;
        return slot < 0 ? Optional.empty() : mapSpawn((slot + offset) % MAX_PARTICIPANTS + 1);
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
            if (isMode(DuelMode.SUMO)) {
                player.teleport(sumoArena.spectatorSpawn());
            }
            player.sendMessage(messages.get("join.spectator"));
        }
        ratings.load(player, this::refreshScoreboards);
        Tasks.sync(plugin, this::refreshScoreboards);
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void vanishChanged(Player player, boolean vanished) {
        if (vanished && isIngame() && isParticipant(player)) {
            quit(player);
            round.watch(player);
        }
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
                    gameActive = false;
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

    /** The maps of {@code mode} with a spawn for every slot, by number. */
    public List<Integer> playableMaps(DuelMode mode) {
        if (mode == DuelMode.SUMO) {
            return List.of(1);
        }
        return IntStream.rangeClosed(1, config.maps(mode).size())
                .filter(map -> spawns.hasMapSpawns(mode, map, MAX_PARTICIPANTS))
                .boxed()
                .toList();
    }

    public String mapName(DuelMode mode, int map) {
        return mode == DuelMode.SUMO ? messages.raw("sumo.map-name") : config.maps(mode).get(map - 1);
    }

    public boolean sumoActive(Player player) {
        return isPlaying(DuelMode.SUMO) && pvp && !frozen && isParticipant(player)
                && sumoMatch.state() == SumoMatch.State.ACTIVE && sumoArena.owns(player.getLocation());
    }

    public boolean sumoLost(Location location) {
        return sumoArena.lost(location);
    }

    public void sumoFall(Player victim) {
        if (!sumoActive(victim) || !sumoMatch.fall(victim.getUniqueId())) {
            return;
        }
        pvp = false;
        frozen = true;
        summary.death(victim);
        opponentsOf(victim).forEach(scorer -> {
            summary.awardKill(scorer, victim, Defeat.POINT);
            gameKills.merge(scorer.getUniqueId(), 1, Integer::sum);
            Bukkit.broadcast(messages.get("sumo.point", Placeholder.unparsed("player", scorer.getName()),
                    Formatter.number("score", sumoMatch.score(scorer.getUniqueId())),
                    Formatter.number("target", config.sumo().pointsToWin())));
        });
        resetSumoPlayers();
        if (sumoMatch.state() == SumoMatch.State.FINISHED) {
            finishGame(sumoMatch.winner().map(Bukkit::getPlayer));
            return;
        }
        refreshScoreboards();
        round.timer(RoundStart.freeze(plugin, config.sumo().resetSeconds(), round::players,
                messages.get("sumo.next-point"), () -> {
                    if (isPlaying(DuelMode.SUMO)) {
                        sumoMatch.ready();
                        frozen = false;
                        pvp = true;
                    }
                }));
    }

    private void resetSumoPlayers() {
        for (var player : round.players()) {
            if (!player.isDead()) {
                PlayerReset.reset(player, GameMode.SURVIVAL);
                config.combat().apply(player);
                participantSpawn(player).ifPresent(player::teleport);
                player.setVelocity(new Vector());
            }
        }
    }

    public void handleDeath(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        if (!isIngame() || !gameActive || frozen || !isParticipant(victim)) {
            if (isIngame() && isParticipant(victim)) {
                round.respawn(victim, RESPAWN_DELAY_TICKS);
            }
            return;
        }
        if (mode == DuelMode.SUPERJUMP) {
            fell(victim);
            round.respawn(victim, RESPAWN_DELAY_TICKS);
            return;
        }
        if (mode == DuelMode.SUMO) {
            sumoFall(victim);
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
        gameKills.merge(killer.getUniqueId(), 1, Integer::sum);
        summary.death(victim);
        refreshScoreboards();
        round.respawn(victim, RESPAWN_DELAY_TICKS);
        if (vsRound > config.vsKillsToEnd()) {
            var winner = gamePoints(victim) > gamePoints(killer) ? victim : killer;
            finishGame(Optional.of(winner));
            return;
        }
        var title = Title.title(messages.get("title.vs-round", Formatter.number("round", vsRound)), messages.get("title.start"));
        BlockcraftApi.get().notifications().important(killer, title);
    }

    /** Whoever dies loses the game, whether the opponent or the map killed them. */
    private void handleSgDeath(Player victim, Optional<Player> killer) {
        summary.death(victim);
        killer.ifPresent(slayer -> summary.awardKill(slayer, victim, Defeat.OUT));
        finishGame(opponentsOf(victim).findFirst());
        round.respawn(victim, RESPAWN_DELAY_TICKS);
    }

    /** Returns true if this checkpoint is new for the player. */
    public boolean reachCheckpoint(Player player, int checkpoint) {
        if (!isPlaying(DuelMode.SUPERJUMP) || frozen || !isParticipant(player)
                || checkpoints.getOrDefault(player.getUniqueId(), 0) >= checkpoint) {
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
        if (!isPlaying(DuelMode.SUPERJUMP) || frozen || superJumpFinished) {
            return;
        }
        superJumpFinished = true;
        if (!fallen.contains(winner.getUniqueId())) {
            summary.moment(winner, "duell", "trittsicher");
        }
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(messages.get("superjump.goal", Placeholder.unparsed("player", winner.getName())));
        Bukkit.broadcast(Component.empty());
        winner.getWorld().spawnParticle(Particle.EXPLOSION, winner.getLocation(), 5);
        winner.setRespawnLocation(winner.getLocation(), true);
        finishGame(Optional.of(winner));
    }

    public void fell(Player player) {
        fallen.add(player.getUniqueId());
    }

    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (mode == null || !isParticipant(player)) {
            return;
        }
        var spawn = !gameActive ? lobbySpawn().or(() -> participantSpawn(player))
                : mode == DuelMode.SUPERJUMP
                ? Optional.ofNullable(player.getRespawnLocation()).or(() -> participantSpawn(player))
                : participantSpawn(player);
        spawn.ifPresent(event::setRespawnLocation);
        int identity = gameIdentity;
        if (gameActive && mode == DuelMode.VS) {
            removeGameEntities();
            opponentsOf(player).filter(other -> !other.isDead()).forEach(other -> {
                participantSpawn(other).ifPresent(other::teleport);
                preparePlayer(other);
            });
        }
        Tasks.sync(plugin, () -> {
            if (!player.isOnline() || player.isDead() || !isParticipant(player) || gameIdentity != identity) {
                return;
            }
            if (gameActive && isIngame()) {
                preparePlayer(player);
            } else {
                PlayerReset.reset(player, GameMode.ADVENTURE);
                lobbyHotbar.giveHub(player);
            }
        });
    }

    public void refreshScoreboards() {
        if (round.phase() == GamePhase.ENDING) {
            var winners = summary.winnerNames();
            Bukkit.getOnlinePlayers().forEach(viewer -> scoreboard.showEnd(viewer, winners));
            return;
        }
        var board = new GameScoreboard.Board(modeLine(), mapLine(),
                roundTimer == null ? config.countdowns().round() : roundTimer.remaining(), tourProgress());
        participants().forEach(participant -> scoreboard.showDuellist(participant, board, ratings.placeholders(participant),
                opponentsOf(participant).filter(participant::canSee).map(Player::getName).toList(),
                isPlaying(DuelMode.VS) || isPlaying(DuelMode.SUMO)
                        ? OptionalInt.of(gamePoints(participant)) : OptionalInt.empty(), isMode(DuelMode.SUMO), tourScore(participant)));
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
        if (isPlaying(DuelMode.SUMO)) {
            return messages.get("scoreboard.spectator-points", name,
                    Formatter.number("points", sumoMatch.score(duellist.getUniqueId())));
        }
        if (isPlaying(DuelMode.VS)) {
            return messages.get("scoreboard.spectator-kills", name, Formatter.number("kills", gamePoints(duellist)));
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
        var playable = new EnumMap<DuelMode, List<Integer>>(DuelMode.class);
        for (var candidate : DuelMode.values()) {
            playable.put(candidate, playableMaps(candidate));
        }
        var requested = selectedFormat();
        var schedule = DuelSeries.plan(requested, votes, playable, random);
        series = new DuelSeries(duellists, schedule);
        round.enter(GamePhase.INGAME);
        recognizeOpponents();
        if (requested == DuelSeries.Format.TOUR && schedule.size() == 1) {
            Bukkit.broadcast(messages.get("tour.fallback"));
        } else if (isTour()) {
            var modes = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")),
                    schedule.stream().map(game -> modeName(game.mode())).toList());
            Bukkit.broadcast(messages.get("tour.schedule", Placeholder.component("modes", modes)));
        }
        startGame(config.countdowns().freeze());
    }

    private void startGame(int freezeSeconds) {
        if (!isIngame() || series == null || series.finished() || round.playerCount() != MAX_PARTICIPANTS) {
            return;
        }
        removeGameEntities();
        var outcome = series.current().orElseThrow();
        mode = outcome.mode();
        mapNumber = outcome.map();
        mapName = mode == DuelMode.SUMO || !config.maps(mode).isEmpty() ? mapName(mode, mapNumber) : null;
        gameIdentity++;
        vsRound = 1;
        superJumpFinished = false;
        checkpoints.clear();
        fallen.clear();
        gameKills.clear();
        summary.nextGame();
        sumoMatch = new SumoMatch(config.sumo().pointsToWin());
        frozen = true;
        pvp = false;
        gameActive = true;
        round.enter(GamePhase.INGAME);
        if (mode == DuelMode.SUMO) {
            sumoMatch.start(duellists);
            Bukkit.broadcast(messages.get("sumo.started", Formatter.number("target", config.sumo().pointsToWin())));
        }
        Bukkit.broadcast(messages.get("vote.result", Placeholder.component("mode", modeName(mode)),
                Placeholder.component("map", mapName == null ? messages.get("vote.random-map") : Component.text(mapName))));
        for (var player : round.players()) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            preparePlayer(player);
            participantSpawn(player).ifPresent(spawn -> {
                player.teleport(spawn);
                player.setRespawnLocation(spawn, true);
            });
        }
        var spectatorSpawn = mode == DuelMode.SUMO ? Optional.of(sumoArena.spectatorSpawn())
                : mapSpawn(1).map(location -> location.clone().add(0, 3, 0));
        spectatorSpawn.ifPresent(spawn -> round.spectators().forEach(player -> player.teleport(spawn)));
        refreshScoreboards();
        freeze(freezeSeconds);
    }

    private void preparePlayer(Player player) {
        PlayerReset.reset(player, GameMode.SURVIVAL);
        player.setVelocity(new Vector());
        if (mode != DuelMode.SUPERJUMP) {
            config.combat().apply(player);
        }
        if (mode == DuelMode.VS) {
            items.giveVsKit(player);
        }
    }

    private void removeGameEntities() {
        if (mode == null) {
            return;
        }
        mapSpawn(1).ifPresent(spawn -> {
            spawn.getWorld().getEntitiesByClass(Projectile.class).forEach(Entity::remove);
            spawn.getWorld().getEntitiesByClass(Item.class).forEach(Entity::remove);
        });
    }

    private void freeze(int seconds) {
        frozen = true;
        var introduction = messages.get("title.introduction",
                Placeholder.component("mode", modeName(mode)), Placeholder.component("map", mapLine()));
        int identity = gameIdentity;
        round.timer(RoundStart.freeze(plugin, seconds, round::players, introduction, () -> {
            if (!isIngame() || !gameActive || gameIdentity != identity) {
                return;
            }
            frozen = false;
            pvp = mode != DuelMode.SUPERJUMP;
            startRoundTimer();
        }));
    }

    private void startRoundTimer() {
        int identity = gameIdentity;
        roundTimer = round.clock(new TimerBar(messages, "timer.round", config.countdowns().round()), remaining -> {
            if (round.clockOnSidebar()) {
                refreshScoreboards();
            }
        }, () -> {
            if (isIngame() && gameActive && gameIdentity == identity) {
                endByTimeout();
            }
        });
    }

    /** The clock decides: VS and Sumo by points, SuperJump by the furthest checkpoint, SG is a draw. */
    private void endByTimeout() {
        var winner = switch (mode) {
            case VS, SUMO -> leaderBy(this::gamePoints);
            case SUPERJUMP -> leaderBy(player -> checkpoints.getOrDefault(player.getUniqueId(), 0));
            case SG -> Optional.<Player>empty();
        };
        finishGame(winner);
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
        gameActive = false;
        pvp = false;
        frozen = false;
        Bukkit.broadcast(messages.get("round.ended"));
        startRestart();
    }

    private int gamePoints(Player player) {
        return mode == DuelMode.SUMO ? sumoMatch.score(player.getUniqueId())
                : gameKills.getOrDefault(player.getUniqueId(), 0);
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

    private void finishGame(Optional<Player> winner) {
        if (!gameActive || series == null || !series.complete(series.gameIndex(), winner.map(Player::getUniqueId))) {
            return;
        }
        gameActive = false;
        pvp = false;
        frozen = true;
        round.stop();
        roundTimer = null;
        winner.filter(player -> mode == DuelMode.VS || mode == DuelMode.SG)
                .filter(summary::undamaged).ifPresent(player -> summary.moment(player, "duell", "makellos"));
        if (isTour()) {
            var result = winner.map(player -> messages.get("tour.game-win",
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
                        Formatter.number("score", series.wins(player.getUniqueId())),
                        Formatter.number("games", series.schedule().size()))));
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
        // A mode must not change while the previous death event is still being dispatched.
        Tasks.sync(plugin, () -> {
            if (isIngame() && !gameActive && series.gameIndex() == nextIndex) {
                startGame(Math.toIntExact(config.tourIntermission().toSeconds()));
            }
        });
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
        summary.awardWin(winner);
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

    public void shutdown() {
        lobbyCountdown.cancel();
        round.stop();
    }
}
