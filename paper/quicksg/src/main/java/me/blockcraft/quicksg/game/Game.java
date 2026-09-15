package me.blockcraft.quicksg.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class Game {

    private static final Set<Integer> PROTECTION_ANNOUNCEMENTS = Set.of(15, 10, 5, 4, 3, 2, 1);
    private static final Set<Integer> GAME_END_ANNOUNCEMENTS = Set.of(600, 300, 180);
    private static final double INGAME_MAX_HEALTH = 14.0;
    private static final long RESPAWN_DELAY_TICKS = 1L;

    private final Plugin plugin;
    private final QuickSGConfig config;
    private final Messages messages;
    private final Locations<LocationKey> locations;
    private final StatsRepository stats;
    private final GameScoreboard scoreboard;
    private final GameItems items;

    private GamePhase phase = GamePhase.LOBBY;
    private boolean protection;
    private boolean frozen;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, Kit> kits = new HashMap<>();
    private final LobbyCountdown lobbyCountdown;
    private final RoundSummary summary;
    private final Arena arena;
    private Countdown freezeCountdown;
    private Countdown protectionCountdown;
    private Countdown gameTimer;
    private Countdown restartCountdown;
    private TimerBar protectionBar;
    private TimerBar gameBar;

    public Game(Plugin plugin, QuickSGConfig config, Messages messages, Locations<LocationKey> locations,
                StatsRepository stats, GameScoreboard scoreboard, GameItems items) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.locations = locations;
        this.stats = stats;
        this.scoreboard = scoreboard;
        this.items = items;
        this.arena = new Arena(messages);
        this.lobbyCountdown = new LobbyCountdown(plugin, arena, config.countdowns().lobby(), config.minPlayers(), alive::size, this::start);
        this.summary = new RoundSummary(plugin, messages.get("game-name"),
                new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()));
    }

    public boolean isLobby() {
        return phase == GamePhase.LOBBY;
    }

    public boolean isIngame() {
        return phase == GamePhase.INGAME;
    }

    public boolean protection() {
        return protection;
    }

    public boolean frozen() {
        return frozen;
    }

    public boolean isAlive(Player player) {
        return alive.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public Kit kit(Player player) {
        return kits.getOrDefault(player.getUniqueId(), Kit.STARTER);
    }

    public void selectKit(Player player, Kit kit) {
        kits.put(player.getUniqueId(), kit);
        refreshScoreboard(player);
    }

    public Stream<Player> alivePlayers() {
        return alive.stream().map(Bukkit::getPlayer).filter(player -> player != null);
    }

    public void applyLobbyWorldBorder() {
        applyWorldBorder(LocationKey.LOBBY_CENTER, config.worldBorder().lobbyRadius());
    }

    public void join(Player player) {
        if (phase != GamePhase.LOBBY) {
            makeSpectator(player);
            locations.get(LocationKey.SPECTATOR).ifPresent(player::teleport);
            arena.arrive(player);
            refreshScoreboards();
            return;
        }
        alive.add(player.getUniqueId());
        kits.put(player.getUniqueId(), Kit.STARTER);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        locations.get(LocationKey.LOBBY).ifPresent(player::teleport);
        giveLobbyItems(player);
        summary.join(player);
        arena.welcome(player, alive.size(), config.maxPlayers());
        refreshScoreboards();
        lobbyCountdown.start();
    }

    public void quit(Player player) {
        boolean wasAlive = alive.remove(player.getUniqueId());
        boolean wasSpectator = spectators.remove(player.getUniqueId());
        kits.remove(player.getUniqueId());
        if (phase == GamePhase.INGAME && wasAlive) {
            summary.death(player);
            record(player, Stat.LOSSES, 1);
            record(player, Stat.DEATHS, 1);
        }
        if (!wasSpectator) {
            arena.farewell(player, alive.size(), config.maxPlayers());
        }
        forEachTimer(bar -> bar.hide(player));
        refreshScoreboards();
        checkWinner();
    }

    public void handleDeath(Player victim, Player killer) {
        if (phase != GamePhase.INGAME) {
            return;
        }
        var victimName = Placeholder.unparsed("player", victim.getName());
        if (killer != null) {
            Bukkit.broadcast(messages.get("game.killed", victimName, Placeholder.unparsed("killer", killer.getName())));
            record(killer, Stat.POINTS, summary.awardKill(killer, victim));
            record(killer, Stat.KILLS, 1);
        } else {
            Bukkit.broadcast(messages.get("game.died", victimName));
            record(victim, Stat.POINTS, summary.awardCoins(victim, config.rewards().death()));
        }
        summary.death(victim);
        record(victim, Stat.DEATHS, 1);
        record(victim, Stat.LOSSES, 1);
        alive.remove(victim.getUniqueId());
        spectators.add(victim.getUniqueId());
        Tasks.later(plugin, RESPAWN_DELAY_TICKS, () -> {
            if (victim.isOnline() && victim.isDead()) {
                victim.spigot().respawn();
            }
        });
        refreshScoreboards();
        checkWinner();
    }

    /** The spectator state is applied after the respawn; touching health or game mode on a dead player breaks the respawn. */
    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        locations.get(LocationKey.SPECTATOR).ifPresent(event::setRespawnLocation);
        if (spectators.contains(player.getUniqueId())) {
            Tasks.sync(plugin, () -> makeSpectator(player));
        }
    }

    /** Skips a running lobby countdown to its last five seconds; returns false if that is not possible any more. */
    public boolean shortenLobbyCountdown() {
        return lobbyCountdown.shorten(5);
    }

    public void skipGameTimer(int remainingSeconds) {
        if (gameTimer != null) {
            gameTimer.remaining(remainingSeconds);
        }
    }

    public void makeSpectator(Player player) {
        alive.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        PlayerReset.spectate(player);
    }

    public void refreshScoreboards() {
        Bukkit.getOnlinePlayers().forEach(this::refreshScoreboard);
    }

    private void refreshScoreboard(Player player) {
        switch (phase) {
            case LOBBY -> scoreboard.showLobby(player, alive.size(), kit(player));
            case INGAME -> scoreboard.showIngame(player, alive.size(), spectators.size());
            case ENDING -> scoreboard.showEnding(player, alivePlayers().map(Player::getName).toList());
        }
    }

    private void start() {
        setPhase(GamePhase.INGAME);
        protection = true;
        Bukkit.broadcast(messages.get("lobby.started"));
        var spawns = spawns();
        int index = 0;
        for (var player : alivePlayers().toList()) {
            player.getInventory().clear();
            if (!spawns.isEmpty()) {
                player.teleport(spawns.get(index++ % spawns.size()));
            }
            player.setLevel(0);
            player.setExp(0.0f);
            player.setGameMode(GameMode.SURVIVAL);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 5);
            maxHealth(player, INGAME_MAX_HEALTH);
            kit(player).apply(player);
        }
        refreshScoreboards();
        applyWorldBorder(LocationKey.GAME_CENTER, config.worldBorder().ingameRadius());
        frozen = true;
        freezeCountdown = LobbyCountdown.freeze(plugin, config.countdowns().freeze(), () -> alivePlayers().toList(),
                seconds -> messages.get("lobby.freeze-title", Formatter.number("seconds", seconds)), () -> {
                    frozen = false;
                    startProtection();
                    startGameTimer();
                });
    }

    private void startProtection() {
        int seconds = config.countdowns().protection();
        Bukkit.broadcast(messages.get("protection.ends-in", Formatter.number("seconds", seconds)));
        protectionBar = new TimerBar(messages, "timer.protection", seconds);
        protectionCountdown = Countdown.start(plugin, seconds, remaining -> {
            protectionBar.update(remaining);
            if (PROTECTION_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(messages.get("protection.ends-in", Formatter.number("seconds", remaining)));
            }
        }, () -> {
            protection = false;
            protectionBar.hide();
            Bukkit.broadcast(messages.get("protection.over"));
        });
    }

    private void startGameTimer() {
        int seconds = config.countdowns().game();
        int trackerAt = seconds - config.countdowns().trackerAfter();
        gameBar = new TimerBar(messages, "timer.game", seconds);
        gameTimer = Countdown.start(plugin, seconds, remaining -> {
            gameBar.update(remaining);
            if (remaining == trackerAt) {
                giveTrackers();
            }
            if (GAME_END_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(messages.get("game.ends-in-minutes", Formatter.number("minutes", remaining / 60)));
            } else if (remaining == 60) {
                Bukkit.broadcast(messages.get("game.ends-in-one-minute"));
            }
        }, () -> {
            Bukkit.broadcast(messages.get("game.draw"));
            setPhase(GamePhase.ENDING);
            refreshScoreboards();
            startRestart();
        });
    }

    private void giveTrackers() {
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(messages.get("game.tracker", Formatter.number("minutes", config.countdowns().trackerAfter() / 60)));
        Bukkit.broadcast(Component.empty());
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getInventory().addItem(items.tracker());
            Sounds.NOTIFY.play(player);
        }
    }

    private void checkWinner() {
        if (phase != GamePhase.INGAME || alive.size() > 1) {
            return;
        }
        setPhase(GamePhase.ENDING);
        cancel(protectionCountdown);
        cancel(gameTimer);
        cancel(freezeCountdown);
        frozen = false;
        for (var winner : alivePlayers().toList()) {
            record(winner, Stat.WINS, 1);
            record(winner, Stat.POINTS, summary.awardWin(winner));
        }
        refreshScoreboards();
        startRestart();
    }

    private void startRestart() {
        forEachTimer(TimerBar::hide);
        summary.show();
        restartCountdown = ServerRestart.start(plugin, arena, config.countdowns().restart());
    }

    private void giveLobbyItems(Player player) {
        var inventory = player.getInventory();
        inventory.setItem(0, items.stats());
        inventory.setItem(8, items.hub());
        if (player.hasPermission("blockcraft.quicksg.start")) {
            inventory.setItem(3, items.start());
            inventory.setItem(5, items.kits());
        } else {
            inventory.setItem(4, items.kits());
        }
    }

    private List<Location> spawns() {
        return LocationKey.spawns().stream().flatMap(key -> locations.get(key).stream()).toList();
    }

    private void applyWorldBorder(LocationKey center, double radius) {
        if (!config.worldBorder().enabled()) {
            return;
        }
        locations.get(center).ifPresent(location -> {
            var border = location.getWorld().getWorldBorder();
            border.setCenter(location);
            border.setSize(radius * 2);
        });
    }

    private void record(Player player, Stat column, int delta) {
        var uuid = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        Tasks.async(plugin, () -> stats.add(uuid, name, column, delta));
    }

    private static void maxHealth(Player player, double value) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(value);
        if (player.getHealth() > value) {
            player.setHealth(value);
        }
    }

    private void forEachTimer(Consumer<TimerBar> action) {
        Stream.of(protectionBar, gameBar).filter(Objects::nonNull).forEach(action);
    }

    private static void cancel(Countdown countdown) {
        if (countdown != null) {
            countdown.cancel();
        }
    }

    public void shutdown() {
        lobbyCountdown.cancel();
        Stream.of(freezeCountdown, protectionCountdown, gameTimer, restartCountdown).forEach(Game::cancel);
    }

    private void setPhase(GamePhase phase) {
        this.phase = phase;
        BlockcraftApi.get().presence().state(phase.serverState());
    }
}
