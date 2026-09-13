package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.xp.XpReward;
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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class Duel {

    public static final int MAX_PARTICIPANTS = 2;
    private static final Set<Integer> LOBBY_ANNOUNCEMENTS = Set.of(15, 10, 5, 4, 3, 2, 1);
    private static final Set<Integer> ROUND_ANNOUNCEMENTS = Set.of(60, 30, 10);
    private static final Set<Integer> RESTART_ANNOUNCEMENTS = Set.of(5, 4, 3, 2);
    private static final long RESPAWN_DELAY_TICKS = 5L;

    private final Plugin plugin;
    private final DuellConfig config;
    private final Messages messages;
    private final Spawns spawns;
    private final StatsRepository stats;
    private final CoinsRepository coins;
    private final GameScoreboard scoreboard;
    private final GameItems items;
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
    private Countdown lobbyCountdown;
    private Countdown freezeCountdown;
    private Countdown roundTimer;
    private Countdown restartCountdown;

    public Duel(Plugin plugin, DuellConfig config, Messages messages, Spawns spawns, StatsRepository stats,
                CoinsRepository coins, GameScoreboard scoreboard, GameItems items) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.spawns = spawns;
        this.stats = stats;
        this.coins = coins;
        this.scoreboard = scoreboard;
        this.items = items;
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
            player.sendMessage(messages.get("join.spectator"));
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            participants.add(player.getUniqueId());
            Bukkit.broadcast(messages.get("join.broadcast", Placeholder.unparsed("player", player.getName()),
                    Formatter.number("online", participants.size()), Formatter.number("max", MAX_PARTICIPANTS)));
        }
        refreshScoreboards();
        startLobbyCountdownIfReady();
    }

    public void quit(Player player) {
        if (participants.remove(player.getUniqueId())) {
            Bukkit.broadcast(messages.get("quit.broadcast", Placeholder.unparsed("player", player.getName()),
                    Formatter.number("online", participants.size()), Formatter.number("max", MAX_PARTICIPANTS)));
        }
        checkpoints.remove(player.getUniqueId());
        switch (phase) {
            case INGAME -> {
                if (participants.size() <= 1) {
                    participants().forEach(winner -> {
                        broadcastWinner(winner);
                        rewardWinner(winner);
                    });
                    Bukkit.broadcast(messages.get("round.ended"));
                    startRestart();
                }
            }
            case LOBBY -> {
                if (participants.isEmpty()) {
                    mode = null;
                }
                if (lobbyCountdown != null && participants.size() < MAX_PARTICIPANTS) {
                    lobbyCountdown.cancel();
                    lobbyCountdown = null;
                    Bukkit.broadcast(messages.get("lobby.not-enough-players"));
                    Bukkit.getOnlinePlayers().forEach(online -> online.setLevel(0));
                }
                refreshScoreboards();
            }
            case ENDING -> {
            }
        }
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
        BlockcraftApi.get().effects().onKill(killer, victim);
        BlockcraftApi.get().xp().award(killer, XpReward.KILL);
        record(killer, Stat.KILLS);
        record(victim, Stat.DEATHS);
        killer.setLevel(killer.getLevel() + 1);
        respawnLater(victim);
        if (round > config.vsKillsToEnd()) {
            var winner = victim.getLevel() > killer.getLevel() ? victim : killer;
            broadcastWinner(winner);
            showWinTitle(winner);
            rewardWinner(winner);
            endRound();
            return;
        }
        var title = Title.title(messages.get("title.vs-round", Formatter.number("round", round)), messages.get("title.start"));
        participants().forEach(participant -> participant.showTitle(title));
    }

    public void handleSgDeath(Player victim, Player killer) {
        var victimName = Placeholder.unparsed("player", victim.getName());
        if (killer != null) {
            Bukkit.broadcast(messages.get("round.died", victimName));
            BlockcraftApi.get().effects().onKill(killer, victim);
            BlockcraftApi.get().xp().award(killer, XpReward.KILL);
            broadcastWinner(killer);
            rewardWinner(killer);
            record(killer, Stat.KILLS);
            record(victim, Stat.DEATHS);
            record(victim, Stat.LOSSES);
        } else {
            opponentsOf(victim).forEach(winner -> {
                broadcastWinner(winner);
                rewardWinner(winner);
            });
            victim.showTitle(Title.title(messages.get("title.game-over"), Component.empty()));
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
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 5.0f, 10.0f);
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
        showWinTitle(winner);
        rewardWinner(winner);
        Bukkit.broadcast(Component.empty());
        opponentsOf(winner).forEach(other -> other.playSound(other.getLocation(), Sound.ENTITY_BLAZE_DEATH, 5.0f, 10.0f));
        winner.playSound(winner.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 5.0f, 10.0f);
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
                scoreboard.show(participant, modeLine, mapLine, opponentsOf(participant).map(Player::getName).toList()));
    }

    private void startLobbyCountdownIfReady() {
        if (phase != GamePhase.LOBBY || mode == null || lobbyCountdown != null || participants.size() < MAX_PARTICIPANTS) {
            return;
        }
        int seconds = config.countdowns().lobby();
        Bukkit.broadcast(messages.get("lobby.starting-in", Formatter.number("seconds", seconds)));
        lobbyCountdown = Countdown.start(plugin, seconds, remaining -> {
            Bukkit.getOnlinePlayers().forEach(player -> player.setLevel(remaining));
            if (LOBBY_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(messages.get("lobby.starting-in", Formatter.number("seconds", remaining)));
            }
        }, this::startRound);
    }

    private void startRound() {
        lobbyCountdown = null;
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
                }
                case SG -> {
                    player.showTitle(Title.title(messages.get("title.sg"), messages.get("title.start")));
                    player.getInventory().clear();
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
        freezeCountdown = Countdown.start(plugin, config.countdowns().freeze(), remaining -> {
            if (remaining > 0) {
                var title = Title.title(messages.get("title.freeze", Formatter.number("seconds", remaining)), Component.empty());
                participants().forEach(participant -> participant.showTitle(title));
            }
        }, () -> {
            frozen = false;
            pvp = mode != DuelMode.SUPERJUMP;
        });
    }

    private void startRoundTimer() {
        roundTimer = Countdown.start(plugin, config.countdowns().round(), remaining -> {
            if (ROUND_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(restartMessage(remaining));
            }
        }, this::endByTimeout);
    }

    /** The clock decides: VS by kills, SuperJump by the furthest checkpoint, SG is a draw. */
    private void endByTimeout() {
        var winner = switch (mode) {
            case VS -> leaderBy(Player::getLevel);
            case SUPERJUMP -> leaderBy(player -> checkpoints.getOrDefault(player.getUniqueId(), 0));
            case SG -> Optional.<Player>empty();
        };
        winner.ifPresentOrElse(leader -> {
            broadcastWinner(leader);
            showWinTitle(leader);
            rewardWinner(leader);
        }, () -> Bukkit.broadcast(messages.get("round.draw")));
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
        int seconds = config.countdowns().restart();
        Bukkit.broadcast(restartMessage(seconds));
        restartCountdown = ServerRestart.start(plugin, seconds, remaining -> {
            participants().forEach(participant -> participant.setLevel(remaining));
            if (RESTART_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(restartMessage(remaining));
            }
        });
    }

    private Component restartMessage(int seconds) {
        return messages.get("round.restart-in", Formatter.number("seconds", seconds));
    }

    private void broadcastWinner(Player winner) {
        Bukkit.broadcast(messages.get("round.won", Placeholder.unparsed("player", winner.getName())));
    }

    private void showWinTitle(Player winner) {
        winner.showTitle(Title.title(messages.get("title.won"),
                messages.get("title.won-subtitle", Placeholder.unparsed("player", winner.getName()))));
    }

    private void rewardWinner(Player winner) {
        int amount = config.winCoins();
        Bukkit.broadcast(messages.get("round.coins", Placeholder.unparsed("player", winner.getName()), Formatter.number("coins", amount)));
        BlockcraftApi.get().effects().onWin(winner);
        BlockcraftApi.get().xp().award(winner, XpReward.WIN);
        var uuid = winner.getUniqueId();
        var name = BlockcraftApi.get().realName(winner);
        Tasks.async(plugin, () -> {
            coins.add(uuid, name, amount);
            stats.add(uuid, name, Stat.WINS, 1);
        });
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
        Stream.of(lobbyCountdown, freezeCountdown, roundTimer, restartCountdown).forEach(Duel::cancel);
    }
}
