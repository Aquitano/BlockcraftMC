package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.config.DuellConfig;
import me.blockcraft.duell.config.Spawns;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Duel {

    public static final int MAX_PARTICIPANTS = 2;
    private static final long RESPAWN_DELAY_TICKS = 5L;

    private final Plugin plugin;
    private final DuellConfig config;
    private final Messages messages;
    private final Spawns spawns;
    private final StatsRepository stats;
    private final GameScoreboard scoreboard;
    private final GameItems items;
    private final DuelRatings ratings;
    private final Random random = new Random();

    private GamePhase phase = GamePhase.LOBBY;
    private DuelMode mode;
    private boolean pvp;
    private boolean frozen;
    private final List<UUID> participants = new ArrayList<>();
    private int mapNumber;
    private String mapName;
    private int round = 1;
    private boolean superJumpFinished;
    private final Map<UUID, Integer> checkpoints = new HashMap<>();
    private final LobbyCountdown lobbyCountdown;
    private final RoundSummary summary;
    private final Arena arena;
    private Countdown freezeCountdown;
    private Countdown roundTimer;
    private Countdown restartCountdown;
    private TimerBar roundBar;

    public Duel(Plugin plugin, DuellConfig config, Messages messages, Spawns spawns, StatsRepository stats,
                GameScoreboard scoreboard, GameItems items, DuelRatings ratings) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.spawns = spawns;
        this.stats = stats;
        this.scoreboard = scoreboard;
        this.items = items;
        this.ratings = ratings;
        this.arena = new Arena(messages, Sounds.DUELL_JOIN);
        this.lobbyCountdown = new LobbyCountdown(plugin, arena, config.countdowns().lobby(), MAX_PARTICIPANTS, participants::size, this::startRound);
        this.summary = new RoundSummary(plugin, messages,
                new RoundSummary.Rewards(config.killCoins(), config.winCoins()));
    }

    public boolean isLobby() {
        return phase == GamePhase.LOBBY;
    }

    public boolean isIngame() {
        return phase == GamePhase.INGAME;
    }

    public boolean isPlaying(DuelMode mode) {
        return phase == GamePhase.INGAME && this.mode == mode;
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

    public boolean voted() {
        return mode != null;
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public Stream<Player> participants() {
        return participants.stream().map(Bukkit::getPlayer).filter(player -> player != null);
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
        items.giveLobbyItems(player);
        spawns.spawn().ifPresent(player::teleport);
        if (participants.size() >= MAX_PARTICIPANTS || phase != GamePhase.LOBBY) {
            player.setGameMode(GameMode.SPECTATOR);
            arena.arrive(player);
            player.sendMessage(messages.get("join.spectator"));
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            participants.add(player.getUniqueId());
            summary.join(player);
            arena.welcome(player, participants.size(), MAX_PARTICIPANTS);
        }
        ratings.load(player, this::refreshScoreboards);
        refreshScoreboards();
        startLobbyCountdownIfReady();
    }

    /** The player came to watch a friend, and only participants are playing, so everyone else is a spectator. */
    public void follow(Player spectator, UUID friend) {
        if (isParticipant(spectator)) {
            return;
        }
        var target = Bukkit.getPlayer(friend);
        if (target != null) {
            arena.follow(spectator, target);
        }
    }

    public void nickChanged(Player player) {
        ratings.refreshTab(player);
        refreshScoreboards();
    }

    public void quit(Player player) {
        boolean left = participants.remove(player.getUniqueId());
        if (left) {
            arena.farewell(player, participants.size(), MAX_PARTICIPANTS);
        }
        checkpoints.remove(player.getUniqueId());
        hideRoundBar(player);
        switch (phase) {
            case INGAME -> {
                if (left && participants.size() <= 1) {
                    participants().forEach(winner -> rewardWinner(winner, player));
                    Bukkit.broadcast(messages.get("round.ended"));
                    startRestart();
                }
            }
            case LOBBY -> {
                if (participants.isEmpty()) {
                    mode = null;
                }
                if (lobbyCountdown.isRunning() && participants.size() < MAX_PARTICIPANTS) {
                    lobbyCountdown.cancel();
                    Bukkit.broadcast(messages.get("lobby.not-enough-players"));
                }
                refreshScoreboards();
            }
            case ENDING -> {
            }
        }
        ratings.forget(player);
    }

    /** Returns false if a mode was already voted. */
    public boolean vote(DuelMode mode) {
        if (this.mode != null) {
            return false;
        }
        this.mode = mode;
        Bukkit.broadcast(messages.get("vote.ended"));
        refreshScoreboards();
        startLobbyCountdownIfReady();
        return true;
    }

    public void handleVsKill(Player victim, Player killer) {
        round++;
        summary.awardKill(killer, victim);
        summary.death(victim);
        record(killer, Stat.KILLS);
        record(victim, Stat.DEATHS);
        refreshScoreboards();
        respawnLater(victim);
        if (round > config.vsKillsToEnd()) {
            var winner = summary.kills(victim) > summary.kills(killer) ? victim : killer;
            rewardWinner(winner);
            endRound();
            return;
        }
        var title = Title.title(messages.get("title.vs-round", Formatter.number("round", round)), messages.get("title.start"));
        participants().forEach(participant -> participant.showTitle(title));
    }

    public void handleSgDeath(Player victim, Player killer) {
        var victimName = Placeholder.unparsed("player", victim.getName());
        summary.death(victim);
        if (killer != null) {
            Bukkit.broadcast(messages.get("round.died", victimName));
            summary.awardKill(killer, victim);
            rewardWinner(killer);
            record(killer, Stat.KILLS);
            record(victim, Stat.DEATHS);
            record(victim, Stat.LOSSES);
        } else {
            opponentsOf(victim).forEach(this::rewardWinner);
            Bukkit.broadcast(messages.get("round.died", victimName));
        }
        endRound();
        respawnLater(victim);
    }

    /** Returns true if this checkpoint is new for the player. */
    public boolean reachCheckpoint(Player player, int checkpoint) {
        if (checkpoints.getOrDefault(player.getUniqueId(), 0) >= checkpoint) {
            return false;
        }
        checkpoints.put(player.getUniqueId(), checkpoint);
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
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(messages.get("superjump.goal", Placeholder.unparsed("player", winner.getName())));
        rewardWinner(winner);
        Bukkit.broadcast(Component.empty());
        winner.getWorld().spawnParticle(Particle.EXPLOSION, winner.getLocation(), 5);
        winner.setRespawnLocation(winner.getLocation(), true);
        startRestart();
    }

    public void refreshScoreboards() {
        var modeLine = mode == null ? messages.get("scoreboard.mode-none") : messages.get("mode." + mode.configKey());
        var mapLine = phase == GamePhase.LOBBY || mapName == null
                ? messages.get("scoreboard.map-random")
                : Component.text(mapName);
        participants().forEach(participant ->
                scoreboard.show(participant, modeLine, ratings.placeholders(participant), mapLine,
                        opponentsOf(participant).map(Player::getName).toList(),
                        mode == DuelMode.VS && phase != GamePhase.LOBBY ? OptionalInt.of(summary.kills(participant)) : OptionalInt.empty()));
    }

    private void startLobbyCountdownIfReady() {
        if (phase == GamePhase.LOBBY && mode != null && participants.size() >= MAX_PARTICIPANTS) {
            lobbyCountdown.start();
        }
    }

    private void startRound() {
        setPhase(GamePhase.INGAME);
        Bukkit.broadcast(messages.get("lobby.started"));
        var maps = config.maps(mode);
        mapNumber = pickMap(maps.size());
        mapName = maps.isEmpty() ? messages.raw("scoreboard.map-random") : maps.get(mapNumber - 1);
        int slot = 1;
        for (var player : participants().toList()) {
            teleportToMapSpawn(player, slot++);
            switch (mode) {
                case VS -> {
                    player.showTitle(Title.title(messages.get("title.vs-round", Formatter.number("round", round)), messages.get("title.start")));
                    items.giveVsKit(player);
                    config.combat().apply(player);
                }
                case SG -> {
                    player.showTitle(Title.title(messages.get("title.sg"), messages.get("title.start")));
                    player.getInventory().clear();
                    config.combat().apply(player);
                }
                case SUPERJUMP -> {
                    player.showTitle(Title.title(messages.get("title.superjump"), messages.get("title.map", Placeholder.unparsed("map", mapName))));
                    items.giveSuperJumpKit(player);
                    player.setRespawnLocation(player.getLocation(), true);
                }
            }
            player.setLevel(0);
        }
        refreshScoreboards();
        startRoundTimer();
        freeze();
    }

    /** Only maps with a spawn for every slot are drawn; without any, the first map is used and the warning below tells why. */
    private int pickMap(int configured) {
        var playable = IntStream.rangeClosed(1, configured).filter(map -> spawns.hasMapSpawns(mode, map, MAX_PARTICIPANTS)).toArray();
        return playable.length == 0 ? 1 : playable[random.nextInt(playable.length)];
    }

    private void setPhase(GamePhase phase) {
        this.phase = phase;
        BlockcraftApi.get().presence().state(phase.serverState());
    }

    private void teleportToMapSpawn(Player player, int slot) {
        mapSpawn(slot).ifPresentOrElse(player::teleport,
                () -> plugin.getLogger().warning("No spawn set for " + mode.setupKey() + " map " + mapNumber + " player " + slot));
    }

    private void freeze() {
        frozen = true;
        freezeCountdown = LobbyCountdown.freeze(plugin, config.countdowns().freeze(), () -> participants().toList(),
                seconds -> messages.get("title.freeze", Formatter.number("seconds", seconds)), () -> {
                    frozen = false;
                    pvp = mode != DuelMode.SUPERJUMP;
                });
    }

    private void startRoundTimer() {
        int seconds = config.countdowns().round();
        roundBar = new TimerBar(messages, "timer.round", seconds);
        roundTimer = Countdown.start(plugin, seconds, remaining -> roundBar.update(remaining), this::endByTimeout);
    }

    /** The clock decides: VS by kills, SuperJump by the furthest checkpoint, SG is a draw. */
    private void endByTimeout() {
        var winner = switch (mode) {
            case VS -> leaderBy(summary::kills);
            case SUPERJUMP -> leaderBy(player -> checkpoints.getOrDefault(player.getUniqueId(), 0));
            case SG -> Optional.<Player>empty();
        };
        winner.ifPresentOrElse(this::rewardWinner, () -> Bukkit.broadcast(messages.get("round.draw")));
        endRound();
    }

    private Optional<Player> leaderBy(ToIntFunction<Player> score) {
        var players = participants().toList();
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

    private void startRestart() {
        if (phase == GamePhase.ENDING) {
            return;
        }
        setPhase(GamePhase.ENDING);
        cancel(roundTimer);
        cancel(freezeCountdown);
        frozen = false;
        hideRoundBar();
        summary.show();
        restartCountdown = ServerRestart.start(plugin, arena, config.countdowns().restart());
    }

    private void rewardWinner(Player winner) {
        summary.awardWin(winner);
        record(winner, Stat.WINS);
        opponentsOf(winner).findFirst().ifPresent(loser -> ratings.settle(winner, loser, this::refreshScoreboards));
    }

    /** A quitter has already left the participants, so the round names them as the loser itself. */
    private void rewardWinner(Player winner, Player quitter) {
        summary.awardWin(winner);
        record(winner, Stat.WINS);
        ratings.settle(winner, quitter, this::refreshScoreboards);
    }

    private void hideRoundBar() {
        if (roundBar != null) {
            roundBar.hide();
        }
    }

    private void hideRoundBar(Player player) {
        if (roundBar != null) {
            roundBar.hide(player);
        }
    }

    private void record(Player player, Stat column) {
        var uuid = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        Tasks.async(plugin, () -> stats.add(uuid, name, column, 1));
    }

    private void respawnLater(Player player) {
        Tasks.later(plugin, RESPAWN_DELAY_TICKS, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    private static void cancel(Countdown countdown) {
        if (countdown != null) {
            countdown.cancel();
        }
    }

    public void shutdown() {
        lobbyCountdown.cancel();
        hideRoundBar();
        Stream.of(freezeCountdown, roundTimer, restartCountdown).forEach(Duel::cancel);
    }
}
