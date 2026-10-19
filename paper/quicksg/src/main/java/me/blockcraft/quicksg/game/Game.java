package me.blockcraft.quicksg.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.config.LocationKey;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Game {

    public static final int MIN_TO_START = 2;
    private static final Set<Integer> PROTECTION_ANNOUNCEMENTS = Set.of(15, 10, 5, 4, 3, 2, 1);
    private static final double INGAME_MAX_HEALTH = 14.0;
    private static final long RESPAWN_DELAY_TICKS = 1L;
    private static final int SECONDS_PER_MINUTE = 60;
    /** QuickSG's group in the achievement catalog, {@code achievements.quicksg} in BlockcraftAPI's config.yml. */
    private static final String ACHIEVEMENTS = "quicksg";

    private final Plugin plugin;
    private final QuickSGConfig config;
    private final Messages messages;
    private final Locations<LocationKey> locations;
    private final GameScoreboard scoreboard;
    private final GameItems items;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown lobbyCountdown;

    private boolean protection;
    private boolean frozen;
    private final Map<UUID, Kit> kits = new HashMap<>();
    private Countdown gameTimer;

    public Game(Plugin plugin, QuickSGConfig config, Messages messages, Locations<LocationKey> locations,
                StatsRepository stats, GameScoreboard scoreboard, GameItems items) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.locations = locations;
        this.scoreboard = scoreboard;
        this.items = items;
        this.summary = new RoundSummary(plugin, messages,
                new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.QUICKSG_JOIN), summary, config.maxPlayers());
        this.lobbyCountdown = new LobbyCountdown(plugin, round.arena(), config.countdowns().lobby(), config.minPlayers(),
                round::playerCount, this::start);
    }

    public boolean isLobby() {
        return round.phase() == GamePhase.LOBBY;
    }

    public boolean isIngame() {
        return round.phase() == GamePhase.INGAME;
    }

    public boolean protection() {
        return protection;
    }

    public boolean frozen() {
        return frozen;
    }

    public boolean isAlive(Player player) {
        return round.isPlaying(player);
    }

    public boolean isSpectator(Player player) {
        return round.isSpectator(player);
    }

    public Kit kit(Player player) {
        return kits.getOrDefault(player.getUniqueId(), Kit.STARTER);
    }

    public void selectKit(Player player, Kit kit) {
        kits.put(player.getUniqueId(), kit);
        refreshScoreboard(player);
    }

    public List<Player> alivePlayers() {
        return round.players();
    }

    public void applyLobbyWorldBorder() {
        applyWorldBorder(LocationKey.LOBBY_CENTER, config.worldBorder().lobbyRadius());
    }

    public void join(Player player) {
        if (!round.join(player)) {
            locations.get(LocationKey.SPECTATOR).ifPresent(player::teleport);
            refreshScoreboards();
            return;
        }
        kits.put(player.getUniqueId(), Kit.STARTER);
        PlayerReset.reset(player, GameMode.ADVENTURE);
        locations.get(LocationKey.LOBBY).ifPresent(player::teleport);
        giveLobbyItems(player);
        refreshScoreboards();
        lobbyCountdown.start();
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void quit(Player player) {
        round.leave(player);
        kits.remove(player.getUniqueId());
        refreshScoreboards();
        checkWinner(true);
    }

    public void handleDeath(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        if (!isIngame() || !isAlive(victim)) {
            return;
        }
        var death = summary.resolveDeath(event);
        Bukkit.broadcast(death.line());
        death.killer().ifPresentOrElse(killer -> {
            summary.awardKill(killer, victim, Defeat.OUT);
            if (kit(killer) == Kit.STARTER && kit(victim) == Kit.DIAMOND) {
                summary.moment(killer, ACHIEVEMENTS, "david-gegen-goliath", Placeholder.unparsed("player", victim.getName()));
            }
        }, () -> summary.awardCoins(victim, config.rewards().death()));
        summary.death(victim);
        round.eliminate(victim);
        round.respawn(victim, RESPAWN_DELAY_TICKS);
        refreshScoreboards();
        checkWinner(false);
    }

    /** The spectator state is applied after the respawn; touching health or game mode on a dead player breaks the respawn. */
    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        locations.get(LocationKey.SPECTATOR).ifPresent(event::setRespawnLocation);
        if (isSpectator(player)) {
            Tasks.sync(plugin, () -> round.watch(player));
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

    public void refreshScoreboards() {
        Bukkit.getOnlinePlayers().forEach(this::refreshScoreboard);
    }

    private void refreshScoreboard(Player player) {
        switch (round.phase()) {
            case LOBBY -> scoreboard.showLobby(player, round.playerCount(), kit(player));
            case INGAME -> scoreboard.showIngame(player, round.playerCount(), round.spectatorCount());
            case ENDING -> scoreboard.showEnding(player, summary.winnerNames());
        }
    }

    private void start() {
        if (round.playerCount() < MIN_TO_START) {
            Bukkit.broadcast(messages.get("lobby.cancelled"));
            lobbyCountdown.start();
            return;
        }
        round.enter(GamePhase.INGAME);
        protection = true;
        Bukkit.broadcast(messages.get("lobby.started"));
        var spawns = spawns();
        int index = 0;
        for (var player : alivePlayers()) {
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
            config.combat().apply(player);
        }
        refreshScoreboards();
        applyWorldBorder(LocationKey.GAME_CENTER, config.worldBorder().ingameRadius());
        frozen = true;
        round.timer(LobbyCountdown.freeze(plugin, config.countdowns().freeze(), round::players,
                seconds -> messages.get("lobby.freeze-title", Formatter.number("seconds", seconds)), () -> {
                    frozen = false;
                    startProtection();
                    startGameTimer();
                }));
    }

    private void startProtection() {
        int seconds = config.countdowns().protection();
        Bukkit.broadcast(messages.get("protection.ends-in", Formatter.number("seconds", seconds)));
        var protectionBar = round.bar(new TimerBar(messages, "timer.protection", seconds));
        round.timer(Countdown.start(plugin, seconds, remaining -> {
            protectionBar.update(remaining);
            if (PROTECTION_ANNOUNCEMENTS.contains(remaining)) {
                Bukkit.broadcast(messages.get("protection.ends-in", Formatter.number("seconds", remaining)));
            }
        }, () -> {
            protection = false;
            protectionBar.hide();
            Bukkit.broadcast(messages.get("protection.over"));
            alivePlayers().forEach(player -> {
                Sounds.PVP_ENABLED.play(player);
                BlockcraftApi.get().notifications().important(player, messages.get("protection.fight"));
            });
        }));
    }

    private void startGameTimer() {
        int seconds = config.countdowns().game();
        int trackerAfter = config.countdowns().trackerAfter();
        var gameBar = round.bar(new TimerBar(messages, "timer.game", seconds));
        gameTimer = round.timer(Countdown.start(plugin, seconds, remaining -> {
            gameBar.update(remaining);
            if (remaining == seconds - trackerAfter) {
                round.handOutTrackers(trackerAfter / SECONDS_PER_MINUTE, items.tracker());
            }
            round.announceEnd(remaining);
        }, () -> {
            Bukkit.broadcast(messages.get("game.draw"));
            endRound();
        }));
    }

    /** A win handed over by a leaving opponent earns no moments, so two accounts cannot farm them. */
    private void checkWinner(boolean opponentLeft) {
        if (!isIngame() || round.playerCount() > 1) {
            return;
        }
        for (var winner : alivePlayers()) {
            summary.awardWin(winner);
            if (opponentLeft) {
                continue;
            }
            if (summary.kills(winner) == 0) {
                summary.moment(winner, ACHIEVEMENTS, "pazifist");
            }
            if (Arrays.stream(winner.getInventory().getArmorContents()).allMatch(piece -> piece == null || piece.isEmpty())) {
                summary.moment(winner, ACHIEVEMENTS, "nackt");
            }
        }
        endRound();
    }

    private void endRound() {
        frozen = false;
        round.end(config.countdowns().restart());
        refreshScoreboards();
    }

    private void giveLobbyItems(Player player) {
        var inventory = player.getInventory();
        inventory.setItem(0, items.hotbar(player, GameItem.STATS));
        inventory.setItem(8, items.hotbar(player, GameItem.HUB));
        if (player.hasPermission("blockcraft.quicksg.start")) {
            inventory.setItem(3, items.hotbar(player, GameItem.START));
            inventory.setItem(5, items.hotbar(player, GameItem.KITS));
        } else {
            inventory.setItem(4, items.hotbar(player, GameItem.KITS));
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

    private static void maxHealth(Player player, double value) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(value);
        if (player.getHealth() > value) {
            player.setHealth(value);
        }
    }

    public void shutdown() {
        lobbyCountdown.cancel();
        round.stop();
    }
}
