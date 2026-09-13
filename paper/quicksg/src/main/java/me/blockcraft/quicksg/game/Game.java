package me.blockcraft.quicksg.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class Game {

    private static final Set<Integer> LOBBY_ANNOUNCEMENTS = Set.of(15, 10, 5, 4, 3, 2, 1);
    private static final Set<Integer> PROTECTION_ANNOUNCEMENTS = Set.of(15, 10, 5, 4, 3, 2, 1);
    private static final Set<Integer> GAME_END_ANNOUNCEMENTS = Set.of(600, 300, 180);
    private static final double INGAME_MAX_HEALTH = 14.0;
    private static final long RESPAWN_DELAY_TICKS = 1L;

    private final Plugin plugin;
    private final QuickSGConfig config;
    private final Messages messages;
    private final Locations<LocationKey> locations;
    private final StatsRepository stats;
    private final CoinsRepository coins;
    private final GameScoreboard scoreboard;
    private final GameItems items;

    private GamePhase phase = GamePhase.LOBBY;
    private boolean protection;
    private boolean frozen;
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Set<UUID> builders = new HashSet<>();
    private final Map<UUID, Kit> kits = new HashMap<>();
    private Countdown lobbyCountdown;
    private Countdown freezeCountdown;
    private Countdown protectionCountdown;
    private Countdown gameTimer;
    private Countdown restartCountdown;

    public Game(Plugin plugin, QuickSGConfig config, Messages messages, Locations<LocationKey> locations,
                StatsRepository stats, CoinsRepository coins, GameScoreboard scoreboard, GameItems items) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.locations = locations;
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

    public boolean isBuilder(Player player) {
        return builders.contains(player.getUniqueId());
    }

    /** Returns whether build mode is enabled after the toggle. */
    public boolean toggleBuilder(Player player) {
        if (builders.remove(player.getUniqueId())) {
            return false;
        }
        builders.add(player.getUniqueId());
        return true;
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
            refreshScoreboards();
            return;
        }
        alive.add(player.getUniqueId());
        kits.put(player.getUniqueId(), Kit.STARTER);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        locations.get(LocationKey.LOBBY).ifPresent(player::teleport);
        giveLobbyItems(player);
        Bukkit.broadcast(messages.get("join.broadcast", Placeholder.unparsed("player", player.getName())));
        refreshScoreboards();
        if (Bukkit.getOnlinePlayers().size() >= config.minPlayers()) {
            startLobbyCountdown();
        }
    }

    public void quit(Player player) {
        boolean wasAlive = alive.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        builders.remove(player.getUniqueId());
        kits.remove(player.getUniqueId());
        if (phase == GamePhase.INGAME && wasAlive) {
            record(player, Stat.LOSSES, 1);
            record(player, Stat.DEATHS, 1);
        }
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
            BlockcraftApi.get().effects().onKill(killer, victim);
            reward(killer, config.rewards().kill());
            record(killer, Stat.KILLS, 1);
        } else {
            Bukkit.broadcast(messages.get("game.died", victimName));
            reward(victim, config.rewards().death());
        }
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
        if (lobbyCountdown == null) {
            startLobbyCountdown();
        }
        if (lobbyCountdown.remaining() <= 5) {
            return false;
        }
        lobbyCountdown.remaining(6);
        return true;
    }

    public void skipGameTimer(int remainingSeconds) {
        if (gameTimer != null) {
            gameTimer.remaining(remainingSeconds);
        }
    }

    public void makeSpectator(Player player) {
        alive.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        player.setExp(0.0f);
        player.setLevel(0);
        PlayerReset.resetHealth(player);
        player.setFoodLevel(20);
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
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

    private void startLobbyCountdown() {
        if (lobbyCountdown != null) {
            return;
        }
        int seconds = config.countdowns().lobby();
        lobbyCountdown = Countdown.start(plugin, seconds, remaining -> {
            if (Bukkit.getOnlinePlayers().size() < config.minPlayers()) {
                Bukkit.getOnlinePlayers().forEach(player -> player.setLevel(0));
                lobbyCountdown.remaining(seconds);
                return;
            }
            alivePlayers().forEach(player -> player.setLevel(remaining));
            if (LOBBY_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(messages.get("lobby.starting-in", Formatter.number("seconds", remaining)));
            }
        }, this::start);
    }

    private void start() {
        lobbyCountdown = null;
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
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 4.0f, 4.0f);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 5);
            maxHealth(player, INGAME_MAX_HEALTH);
            kit(player).apply(player);
        }
        refreshScoreboards();
        applyWorldBorder(LocationKey.GAME_CENTER, config.worldBorder().ingameRadius());
        frozen = true;
        freezeCountdown = Countdown.start(plugin, config.countdowns().freeze(), remaining -> {
            if (remaining > 0) {
                var title = Title.title(messages.get("lobby.freeze-title", Formatter.number("seconds", remaining)), Component.empty());
                alivePlayers().forEach(player -> player.showTitle(title));
            }
        }, () -> {
            frozen = false;
            startProtection();
            startGameTimer();
        });
    }

    private void startProtection() {
        int seconds = config.countdowns().protection();
        Bukkit.broadcast(messages.get("protection.ends-in", Formatter.number("seconds", seconds)));
        protectionCountdown = Countdown.start(plugin, seconds, remaining -> {
            if (PROTECTION_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(messages.get("protection.ends-in", Formatter.number("seconds", remaining)));
            }
        }, () -> {
            protection = false;
            Bukkit.broadcast(messages.get("protection.over"));
        });
    }

    private void startGameTimer() {
        int seconds = config.countdowns().game();
        int trackerAt = seconds - config.countdowns().trackerAfter();
        gameTimer = Countdown.start(plugin, seconds, remaining -> {
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
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 4.0f, 4.0f);
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
            Bukkit.broadcast(messages.get("game.winner", Placeholder.unparsed("player", winner.getName())));
            record(winner, Stat.WINS, 1);
            reward(winner, config.rewards().win());
            BlockcraftApi.get().effects().onWin(winner);
        }
        refreshScoreboards();
        startRestart();
    }

    private void startRestart() {
        int seconds = config.countdowns().restart();
        Bukkit.broadcast(restartMessage(seconds));
        restartCountdown = ServerRestart.start(plugin, seconds, remaining -> {
            if (remaining == 5) {
                clearDroppedItems();
            }
            if (remaining >= 1 && remaining <= 5) {
                Bukkit.broadcast(restartMessage(remaining));
            }
            if (remaining == 0) {
                Bukkit.broadcast(messages.get("restart.now"));
            }
        });
    }

    private Component restartMessage(int seconds) {
        return messages.get("restart.in", Formatter.number("seconds", seconds));
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

    private void clearDroppedItems() {
        for (var world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(Item.class).forEach(Item::remove);
        }
    }

    private void reward(Player player, int amount) {
        var uuid = player.getUniqueId();
        var name = player.getName();
        Tasks.async(plugin, () -> {
            coins.add(uuid, name, amount);
            stats.add(uuid, name, Stat.POINTS, amount);
        });
    }

    private void record(Player player, Stat column, int delta) {
        var uuid = player.getUniqueId();
        var name = player.getName();
        Tasks.async(plugin, () -> stats.add(uuid, name, column, delta));
    }

    private static void maxHealth(Player player, double value) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(value);
        if (player.getHealth() > value) {
            player.setHealth(value);
        }
    }

    private static void cancel(Countdown countdown) {
        if (countdown != null) {
            countdown.cancel();
        }
    }

    public void shutdown() {
        Stream.of(lobbyCountdown, freezeCountdown, protectionCountdown, gameTimer, restartCountdown).forEach(Game::cancel);
    }

    private void setPhase(GamePhase phase) {
        this.phase = phase;
        BlockcraftApi.get().presence().state(phase.serverState());
    }
}
