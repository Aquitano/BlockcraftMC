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
import me.blockcraft.mlgrush.arena.RushArena;
import me.blockcraft.mlgrush.config.RushConfig;
import me.blockcraft.mlgrush.config.MapPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RushGame {

    private static final int MAX_PARTICIPANTS = 2;

    private final Plugin plugin;
    private final RushConfig config;
    private final GameConfig gameConfig;
    private final MapPool maps;
    private final StatsRepository stats;
    private final Messages messages;
    private final RushArena arena;
    private RushMatch match;
    private final RushKit kit;
    private RoundSummary summary;
    private final SerialTasks<UUID> recapWrites;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final Sidebar sidebar;
    private final BukkitTask voidCheck;
    private BukkitTask resetTask;

    public RushGame(Plugin plugin, GameConfig gameConfig, RushConfig config, Messages messages,
                    RushArena arena, MapPool maps, StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.gameConfig = gameConfig;
        this.maps = maps;
        this.stats = stats;
        this.messages = messages;
        this.arena = arena;
        match = new RushMatch(config.scoreTarget());
        kit = new RushKit(config.kit(), messages);
        recapWrites = new SerialTasks<>(plugin);
        summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(0, config.winCoins()), stats, recapWrites);
        round = new Round(plugin, new Arena(messages, Sounds.DUELL_JOIN), summary, MAX_PARTICIPANTS, gameConfig);
        countdown = new LobbyCountdown(plugin, round, gameConfig, this::start);
        hotbar = new LobbyHotbar(plugin, List.of(new LobbyHotbar.Choice(MenuIcon.DUELL,
                messages.get("item.rematch"), () -> round.phase() == GamePhase.ENDING,
                player -> requestRematch(player, match))), statsMenus, countdown);
        sidebar = new Sidebar(messages.get("scoreboard.title"));
        voidCheck = Bukkit.getScheduler().runTaskTimer(plugin, this::checkVoid, 1L, 1L);
    }

    public LobbyCountdown countdown() {
        return countdown;
    }

    public LobbyHotbar hotbar() {
        return hotbar;
    }

    public boolean active(Player player) {
        return round.phase() == GamePhase.INGAME && match.state() == RushMatch.State.ACTIVE
                && round.isPlaying(player) && arena.owns(player.getLocation());
    }

    public int slot(Player player) {
        return round.phase() == GamePhase.LOBBY ? round.players().indexOf(player) : match.slot(player.getUniqueId());
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
        } else if (participant && match.leave(player.getUniqueId())) {
            Bukkit.broadcast(messages.get("round.forfeit", Placeholder.unparsed("player", player.getName())));
            finish();
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
        if (round.phase() != GamePhase.LOBBY || !match.start(round.players().stream().map(Player::getUniqueId).toList())) {
            return;
        }
        countdown.cancel();
        round.enter(GamePhase.INGAME);
        round.players().forEach(this::respawn);
        arena.reset();
        Bukkit.broadcast(messages.get("round.start", Formatter.number("target", config.scoreTarget()),
                Placeholder.unparsed("map", arena.name())));
        refresh();
    }

    public void bed(Player player, int owner) {
        if (!active(player) || !match.score(player.getUniqueId(), owner)) {
            return;
        }
        Bukkit.broadcast(messages.get("round.point", Placeholder.unparsed("player", player.getName()),
                Formatter.number("red_score", match.score(0)), Formatter.number("blue_score", match.score(1))));
        round.players().forEach(this::respawn);
        refresh();
        if (match.state() == RushMatch.State.FINISHED) {
            finish();
            return;
        }
        resetTask = Bukkit.getScheduler().runTask(plugin, () -> {
            resetTask = null;
            arena.reset();
            match.ready();
        });
    }

    public void refill(Player player) {
        if (active(player)) {
            kit.refill(player, slot(player));
        }
    }

    private void respawn(Player player) {
        PlayerReset.reset(player, GameMode.SURVIVAL);
        teleport(player, slot(player));
        kit.give(player, slot(player));
    }

    private void teleport(Player player, int slot) {
        player.teleport(arena.spawn(slot));
        player.setVelocity(new Vector());
        player.setFallDistance(0);
    }

    private void checkVoid() {
        for (var player : Bukkit.getOnlinePlayers()) {
            var location = player.getLocation();
            if (arena.owns(location) && location.getY() > config.arena().voidY()) {
                continue;
            }
            if (active(player)) {
                summary.death(player);
                respawn(player);
            } else if (round.isPlaying(player)) {
                teleport(player, Math.max(0, slot(player)));
            } else {
                player.teleport(arena.spectatorSpawn());
                player.setVelocity(new Vector());
                player.setFallDistance(0);
            }
        }
    }

    private void finish() {
        if (round.phase() != GamePhase.INGAME) {
            return;
        }
        cancelReset();
        match.winner().map(Bukkit::getPlayer).ifPresent(summary::awardWin);
        round.players().forEach(player -> {
            summary.note(player, messages.get("round.result", Formatter.number("score", match.score(slot(player))),
                    Formatter.number("target", config.scoreTarget())));
        });
        var completed = match;
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

    private void requestRematch(Player player, RushMatch completed) {
        if (completed != match || round.phase() != GamePhase.ENDING || !round.isPlaying(player)
                || round.playerCount() != MAX_PARTICIPANTS) {
            player.sendMessage(messages.get("rematch.unavailable"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        if (!match.requestRematch(player.getUniqueId())) {
            player.sendMessage(messages.get("rematch.waiting"));
            round.players().stream().filter(opponent -> !opponent.equals(player)).forEach(opponent -> {
                opponent.sendMessage(messages.get("rematch.request", Placeholder.unparsed("player", player.getName())));
                Sounds.NOTIFY.play(opponent);
            });
            return;
        }
        reopen(true);
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
        match = new RushMatch(config.scoreTarget());
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
                            ? players.indexOf(player) == side : match.slot(player.getUniqueId()) == side)
                    .map(Player::getName).findFirst().orElse("-");
            lines.add(messages.get(slot == 0 ? "scoreboard.red" : "scoreboard.blue",
                    Placeholder.unparsed("player", name), Formatter.number("score", match.score(slot))));
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
        countdown.cancel();
        cancelReset();
        voidCheck.cancel();
        match.stop();
        round.stop();
    }
}
