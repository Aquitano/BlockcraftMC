package me.blockcraft.mlgrush.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.task.SerialTasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.api.game.rush.RushArena;
import me.blockcraft.api.game.rush.RushKit;
import me.blockcraft.api.game.rush.RushSession;
import me.blockcraft.api.game.rush.RushConfig;
import me.blockcraft.api.game.rush.MapPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class StandaloneRushGame {

    private static final int MAX_PARTICIPANTS = 2;

    private final Plugin plugin;
    private final RushConfig config;
    private final GameConfig gameConfig;
    private final MapPool maps;
    private final StatsRepository stats;
    private final Messages messages;
    private final RushArena arena;
    private RushSession session;
    private List<UUID> participants = List.of();
    private final java.util.Set<UUID> rematches = new java.util.HashSet<>();
    private Object identity = new Object();
    private final int[] previousScores = new int[2];
    private final RushKit kit;
    private RoundSummary summary;
    private final SerialTasks<UUID> recapWrites;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final Sidebar sidebar;
    private final BukkitTask voidRecovery;
    private BukkitTask resetTask;

    public StandaloneRushGame(Plugin plugin, GameConfig gameConfig, RushConfig config, Messages messages,
                    RushArena arena, MapPool maps, StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.gameConfig = gameConfig;
        this.maps = maps;
        this.stats = stats;
        this.messages = messages;
        this.arena = arena;
        kit = new RushKit(config.kit(), messages);
        recapWrites = new SerialTasks<>(plugin);
        summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(0, config.winCoins()), stats, recapWrites);
        round = new Round(plugin, new Arena(messages, Sounds.DUELL_JOIN), summary, MAX_PARTICIPANTS, gameConfig);
        countdown = new LobbyCountdown(plugin, round, gameConfig, this::start);
        hotbar = new LobbyHotbar(plugin, List.of(new LobbyHotbar.Choice(MenuIcon.DUELL,
                messages.get("item.rematch"), () -> round.phase() == GamePhase.ENDING,
                player -> requestRematch(player, identity))), statsMenus, countdown);
        sidebar = new Sidebar(messages.get("scoreboard.title"));
        voidRecovery = Bukkit.getScheduler().runTaskTimer(plugin, this::recoverWaitingPlayers, 1L, 1L);
    }

    public LobbyCountdown countdown() {
        return countdown;
    }

    public LobbyHotbar hotbar() {
        return hotbar;
    }

    public boolean isIngame() {
        return round.phase() == GamePhase.INGAME;
    }

    public int slot(Player player) {
        return round.phase() == GamePhase.LOBBY ? round.players().indexOf(player) : participants.indexOf(player.getUniqueId());
    }

    public void join(Player player) {
        if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            teleport(player, round.players().indexOf(player));
            hotbar.give(player);
            countdown.start();
        } else {
            player.teleport(arena.spectatorSpawn());
            player.sendMessage(messages.get("join.spectator"));
        }
        player.sendMessage(messages.get("round.map", Placeholder.unparsed("map", arena.name())));
        refreshPresence();
        refresh();
    }

    public void follow(Player player, UUID friend) {
        round.follow(player, friend);
    }

    public void vanished(Player player) {
        if (round.phase() != GamePhase.LOBBY && round.isPlaying(player)) {
            quit(player);
            round.watch(player);
        }
    }

    public void quit(Player player) {
        boolean participant = round.leave(player);
        if (round.phase() == GamePhase.LOBBY) {
            updateLobby();
        } else if (participant && round.phase() == GamePhase.INGAME) {
            Bukkit.broadcast(messages.get("round.forfeit", Placeholder.unparsed("player", player.getName())));
            finish(round.players().stream().map(Player::getUniqueId).findFirst());
        } else if (participant && round.phase() == GamePhase.ENDING) {
            returnToLobbyLater();
        }
        Tasks.sync(plugin, this::refreshPresence);
        refresh();
    }

    public void refreshPresence() {
        BlockcraftApi.get().presence().maxPlayers(advertisedCapacity(Bukkit.getOnlinePlayers().size(), round.playerCount()));
    }

    static int advertisedCapacity(int connected, int participants) {
        return MAX_PARTICIPANTS + connected - participants;
    }

    public void updateLobby() {
        if (round.phase() == GamePhase.LOBBY && round.playerCount() < MAX_PARTICIPANTS) {
            boolean running = countdown.isRunning();
            countdown.cancel();
            if (running) {
                Bukkit.broadcast(messages.get("round.cancelled"));
            }
            seatWaitingPlayers();
            countdown.start();
        }
        refresh();
    }

    private void start() {
        var players = round.players();
        if (round.phase() != GamePhase.LOBBY || players.size() != MAX_PARTICIPANTS
                || players.stream().anyMatch(BlockcraftApi.get()::isVanished)) {
            return;
        }
        countdown.cancel();
        participants = players.stream().map(Player::getUniqueId).toList();
        round.enter(GamePhase.INGAME);
        var started = identity;
        session = new RushSession(plugin, config, arena, kit, participants, new RushSession.Events() {
            @Override
            public void fell(Player player) {
                if (identity == started) {
                    summary.death(player);
                }
            }

            @Override
            public void scoreChanged() {
                if (identity == started) {
                    int scorer = score(0) > previousScores[0] ? 0 : 1;
                    previousScores[0] = score(0);
                    previousScores[1] = score(1);
                    Bukkit.broadcast(messages.get("round.point", Formatter.number("red_score", score(0)),
                            Formatter.number("blue_score", score(1)), Placeholder.unparsed("player", players.get(scorer).getName())));
                    refresh();
                }
            }

            @Override
            public void won(UUID player) {
                if (identity == started) {
                    finish(java.util.Optional.of(player));
                }
            }
        });
        session.activate();
        Bukkit.broadcast(messages.get("round.start", Formatter.number("target", config.scoreTarget()),
                Placeholder.unparsed("map", arena.name())));
        refresh();
    }

    private int score(int slot) {
        return session == null || participants.size() <= slot ? 0 : session.score(participants.get(slot));
    }

    private void recoverWaitingPlayers() {
        if (!isIngame()) {
            for (var player : round.players()) {
                recoverVoid(player, arena.spawn(Math.max(0, slot(player))), config.arena().voidY());
            }
        }
        for (var player : round.spectators()) {
            recoverVoid(player, arena.spectatorSpawn(), config.arena().voidY());
        }
    }

    static void recoverVoid(Player player, Location spawn, int voidY) {
        var location = player.getLocation();
        if (player.isDead() || !java.util.Objects.equals(location.getWorld(), spawn.getWorld()) || location.getY() > voidY) {
            return;
        }
        teleport(player, spawn);
    }

    private void teleport(Player player, int slot) {
        teleport(player, arena.spawn(slot));
    }

    private static void teleport(Player player, Location spawn) {
        player.teleport(spawn);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    private void finish(java.util.Optional<UUID> winner) {
        if (round.phase() != GamePhase.INGAME) {
            return;
        }
        cancelReset();
        winner.map(Bukkit::getPlayer).ifPresent(summary::awardWin);
        if (session != null) {
            session.close();
        }
        round.players().forEach(player -> {
            summary.note(player, messages.get("round.result", Formatter.number("score", score(slot(player))),
                    Formatter.number("target", config.scoreTarget())));
        });
        var completed = identity;
        round.endInPlace(player -> requestRematch(player, completed));
        round.players().forEach(player -> {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            teleport(player, slot(player));
            hotbar.give(player);
        });
        arena.reset();
        if (round.playerCount() < MAX_PARTICIPANTS) {
            returnToLobbyLater();
        }
        refresh();
    }

    private void requestRematch(Player player, Object completed) {
        if (completed != identity || round.phase() != GamePhase.ENDING || !round.isPlaying(player)
                || round.playerCount() != MAX_PARTICIPANTS
                || round.players().stream().anyMatch(BlockcraftApi.get()::isVanished)) {
            player.sendMessage(messages.get("rematch.unavailable"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        if (!rematches.add(player.getUniqueId()) || rematches.size() != MAX_PARTICIPANTS) {
            player.sendMessage(messages.get("rematch.waiting"));
            round.players().stream().filter(opponent -> !opponent.equals(player)).forEach(opponent -> {
                opponent.sendMessage(messages.get("rematch.request", Placeholder.unparsed("player", player.getName())));
                Sounds.NOTIFY.play(opponent);
            });
            return;
        }
        Tasks.sync(plugin, () -> {
            if (identity == completed && round.phase() == GamePhase.ENDING && round.playerCount() == MAX_PARTICIPANTS
                    && round.players().stream().noneMatch(BlockcraftApi.get()::isVanished)) {
                reopen(true);
            }
        });
    }

    private void returnToLobbyLater() {
        cancelReset();
        resetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            resetTask = null;
            if (round.phase() == GamePhase.ENDING && round.playerCount() < MAX_PARTICIPANTS) {
                reopen(false);
            }
        }, gameConfig.startSeconds() * 20L);
    }

    private void reopen(boolean rematch) {
        cancelReset();
        arena.selectMap(maps.choose(ThreadLocalRandom.current()));
        identity = new Object();
        java.util.Arrays.fill(previousScores, 0);
        rematches.clear();
        session = null;
        participants = List.of();
        summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(0, config.winCoins()), stats, recapWrites);
        round.reopen(summary);
        var players = round.players();
        for (int slot = 0; slot < players.size(); slot++) {
            var player = players.get(slot);
            PlayerReset.reset(player, GameMode.ADVENTURE);
            teleport(player, slot);
            hotbar.give(player);
        }
        round.spectators().forEach(player -> player.teleport(arena.spectatorSpawn()));
        if (rematch) {
            Bukkit.broadcast(messages.get("rematch.starting", Formatter.number("seconds", gameConfig.startSeconds()),
                    Placeholder.unparsed("map", arena.name())));
            countdown.shorten(gameConfig.startSeconds());
        } else {
            seatWaitingPlayers();
            countdown.start();
        }
        refreshPresence();
        refresh();
    }

    private void seatWaitingPlayers() {
        round.spectators().stream().filter(player -> !BlockcraftApi.get().isVanished(player))
                .limit(Math.max(0, MAX_PARTICIPANTS - round.playerCount())).forEach(player -> {
                    player.sendMessage(messages.get("join.next-match"));
                    join(player);
                });
    }

    public void refresh() {
        var lines = new ArrayList<Component>();
        lines.add(Component.empty());
        for (int slot = 0; slot < 2; slot++) {
            final int side = slot;
            var players = round.players();
            var name = players.stream().filter(player -> round.phase() == GamePhase.LOBBY
                            ? players.indexOf(player) == side : participants.indexOf(player.getUniqueId()) == side)
                    .map(Player::getName).findFirst().orElse("-");
            lines.add(messages.get(slot == 0 ? "scoreboard.red" : "scoreboard.blue",
                    Placeholder.unparsed("player", name), Formatter.number("score", score(slot))));
        }
        lines.add(Component.empty());
        lines.add(messages.get("scoreboard.map", Placeholder.unparsed("map", arena.name())));
        lines.add(messages.get("scoreboard.target", Formatter.number("target", config.scoreTarget())));
        lines.add(messages.get("scoreboard." + round.phase().name().toLowerCase(java.util.Locale.ROOT)));
        Bukkit.getOnlinePlayers().forEach(player -> sidebar.show(player, lines));
    }

    private void cancelReset() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    public void shutdown() {
        voidRecovery.cancel();
        countdown.cancel();
        cancelReset();
        if (session != null) {
            session.close();
        }
        round.stop();
    }
}
