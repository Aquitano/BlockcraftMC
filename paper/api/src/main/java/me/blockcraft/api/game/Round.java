package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.network.RejoinRepository;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The bookkeeping every game's round shares, held by the game next to its own rules. It seats every joiner: while
 * the lobby is open and has room, they play, unless they are vanished; everyone else watches, and a vanished
 * spectator is neither announced nor counted. A player who vanishes while the lobby waits moves to the spectators
 * the same way. It tells the proxy the phase, owns the countdowns and boss bars that
 * end with the round, counts a mid-round quit as a death and a loss unless the game keeps the quitter's place for a
 * while ({@link #stepOut}), and ends the round once. Its spectators are {@link Spectators}, whose chat stays among
 * themselves while the round runs, so nobody can tell the players where their enemies are.
 */
public final class Round implements Listener {

    private static final Set<Integer> END_ANNOUNCEMENTS = Set.of(600, 300, 180, 60);
    private static final long TICKS_PER_SECOND = 20L;
    private static final int SECONDS_PER_MINUTE = 60;

    private final Plugin plugin;
    private final BlockcraftApi api;
    private final Arena arena;
    private RoundSummary summary;
    private Consumer<Player> backToLobby;
    private final int capacity;
    private final GameConfig config;
    // Chat reads these off the main thread; copy-on-write keeps the join order players() hands out.
    private final Set<UUID> players = new CopyOnWriteArraySet<>();
    private final Spectators spectators;
    private final List<Countdown> timers = new ArrayList<>();
    private final List<TimerBar> bars = new ArrayList<>();
    private final Map<UUID, Away> away = new HashMap<>();
    private final RejoinRepository rejoins;
    private volatile GamePhase phase = GamePhase.LOBBY;
    private PlayArea playArea;
    private Supplier<GameSidebar.Headcount> headcount = () -> new GameSidebar.Headcount(playerCount(), spectatorCount());

    public record PlayArea(World world, BoundingBox box) { }

    public Round(Plugin plugin, Arena arena, RoundSummary summary, int capacity, GameConfig config) {
        this.plugin = plugin;
        this.api = BlockcraftApi.get();
        this.arena = arena;
        this.summary = summary;
        this.capacity = capacity;
        this.config = config;
        this.rejoins = new RejoinRepository(api.database());
        this.spectators = new Spectators(plugin, arena, this::players, () -> phase == GamePhase.INGAME);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        releaseRejoins();
    }

    private record Away(String name, Consumer<UUID> onForfeit) {
    }

    public Arena arena() {
        return arena;
    }

    public RoundSummary summary() {
        return summary;
    }

    public GamePhase phase() {
        return phase;
    }

    /**
     * Open menus and dialogs close, because what they offered may be gone, and the proxy hears the new state. When
     * the lobby gives way to play, the summary learns who plays and everyone hears that the round started.
     */
    public void enter(GamePhase phase) {
        var previous = this.phase;
        boolean starts = this.phase == GamePhase.LOBBY && phase == GamePhase.INGAME;
        this.phase = phase;
        Bukkit.getOnlinePlayers().forEach(player -> {
            Menu.close(player);
            player.closeDialog();
        });
        api.presence().state(phase.serverState());
        if (starts) {
            summary.begin(players());
            arena.announceStarted();
        }
        if (previous != phase) {
            Bukkit.getPluginManager().callEvent(new RoundPhaseChangeEvent(this, previous, phase));
        }
    }

    /** Seats a joiner and returns whether they play; a spectator watches already. */
    public boolean join(Player player) {
        if (phase != GamePhase.LOBBY || full() || api.isVanished(player)) {
            watch(player);
            arena.arrive(player);
            return false;
        }
        players.add(player.getUniqueId());
        // Whoever is online still hides this account if it watched here before and rejoined.
        spectators.release(player);
        summary.join(player);
        arena.welcome(player, players.size(), capacity);
        return true;
    }

    /** Returns whether the player was playing; leaving a running round counts as a death and a loss. */
    public boolean leave(Player player) {
        var id = player.getUniqueId();
        spectators.forget(id);
        bars.forEach(bar -> bar.hide(player));
        if (!players.remove(id)) {
            return false;
        }
        switch (phase) {
            case LOBBY -> summary.forget(player);
            case INGAME -> {
                summary.death(player);
                summary.eliminate(player);
            }
            case ENDING -> {
            }
        }
        arena.farewell(player, headcount().players(), capacity);
        return true;
    }

    /**
     * Keeps a running round's place for {@code rejoin-seconds} when a player drops out: the proxy sends them back
     * here when they reconnect or ask, and {@link #rejoin} seats them again. Staying away runs {@code onForfeit}, and
     * the quit then counts as a death and a loss. False, and nothing kept, when the game or its config keeps no place.
     */
    public boolean stepOut(Player player, Consumer<UUID> onForfeit) {
        var id = player.getUniqueId();
        int seconds = config.rejoinSeconds();
        if (phase != GamePhase.INGAME || seconds <= 0 || !players.contains(id)) {
            return false;
        }
        spectators.forget(id);
        var kept = new Away(player.getName(), onForfeit);
        away.put(id, kept);
        var server = api.server().name();
        Tasks.async(plugin, () -> rejoins.hold(id, server, seconds));
        Tasks.later(plugin, seconds * TICKS_PER_SECOND, () -> {
            if (away.get(id) == kept) {
                forfeit(id);
            }
        });
        arena.steppedOut(player, seconds);
        return true;
    }

    /** Seats a player who stepped out of this round again; false if no place was kept for them here. */
    public boolean rejoin(Player player) {
        var id = player.getUniqueId();
        if (phase != GamePhase.INGAME || away.remove(id) == null) {
            return false;
        }
        release(id);
        spectators.release(player);
        arena.back(player);
        return true;
    }

    /** The place kept for a player who stepped out is lost, as when their time runs out. */
    public void forfeit(UUID id) {
        var kept = away.remove(id);
        if (kept == null) {
            return;
        }
        release(id);
        players.remove(id);
        summary.forfeit(id);
        arena.gaveUp(kept.name());
        kept.onForfeit().accept(id);
    }

    public boolean isAway(UUID id) {
        return away.containsKey(id);
    }

    private void release(UUID id) {
        var server = api.server().name();
        Tasks.async(plugin, () -> rejoins.release(id, server));
    }

    private void releaseRejoins() {
        var server = api.server().name();
        Tasks.async(plugin, () -> rejoins.releaseAll(server));
    }

    @EventHandler
    public void onVanish(VanishChangeEvent event) {
        var player = event.getPlayer();
        if (!event.isVanished() || phase != GamePhase.LOBBY || !players.remove(player.getUniqueId())) {
            return;
        }
        summary.forget(player);
        watch(player);
        arena.farewell(player, headcount().players(), capacity);
    }

    /** Out for good: a dead player watches the rest from their respawn on, a living one once the game calls {@link #watch}. */
    public void eliminate(Player player) {
        players.remove(player.getUniqueId());
        spectators.add(player);
        summary.eliminate(player);
    }

    /**
     * An eliminated player plays again, as when a game revives them: everyone sees them and they count among the
     * players. The death they died still counts. False if the round is not running or they were not out of it.
     */
    public boolean revive(Player player) {
        if (phase != GamePhase.INGAME || player.isDead() || !spectators.isSpectator(player)) {
            return false;
        }
        spectators.release(player);
        players.add(player.getUniqueId());
        summary.revive(player);
        return true;
    }

    /** Puts a spectator into the state they watch in; see {@link Spectators#watch}. */
    public void watch(Player player) {
        spectators.watch(player);
    }

    /** For a game whose players leave the arena when the round ends: whoever respawns after the end goes there too. */
    public void backToLobby(Consumer<Player> sendBack) {
        backToLobby = sendBack;
    }

    /**
     * A player out of the round watches from their respawn on, also after it ended unless the game sends everyone
     * back to its lobby; whoever respawns after the end sees the result.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        var respawnedIn = phase;
        if (respawnedIn != GamePhase.INGAME && respawnedIn != GamePhase.ENDING) {
            return;
        }
        var completed = summary;
        Tasks.sync(plugin, () -> {
            if (phase != respawnedIn || !player.isOnline()) {
                return;
            }
            if (phase == GamePhase.ENDING && backToLobby != null) {
                backToLobby.accept(player);
            } else if (isSpectator(player)) {
                watch(player);
            }
            if (phase == GamePhase.ENDING && summary == completed) {
                completed.showResult(player);
            }
        });
    }

    /** Where the round plays, for whoever records all of it rather than only what is near the players, such as a replay. */
    public void playArea(World world, BoundingBox box) {
        playArea = new PlayArea(world, box.clone());
    }

    public Optional<PlayArea> playArea() {
        return Optional.ofNullable(playArea);
    }

    public boolean isPlaying(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.isSpectator(player);
    }

    public List<Player> players() {
        return online(players);
    }

    public int playerCount() {
        return players.size();
    }

    public boolean full() {
        return players.size() >= capacity;
    }

    public List<Player> spectators() {
        return spectators.list();
    }

    /** Who the round says is still in and how many watch, on the sidebar and when someone leaves. */
    public GameSidebar.Headcount headcount() {
        return headcount.get();
    }

    /** For a game that tells who is in itself, such as one whose deaths stay secret for a while. */
    public void countWith(Supplier<GameSidebar.Headcount> headcount) {
        this.headcount = headcount;
    }

    /** Vanished staff watch uncounted. */
    public int spectatorCount() {
        return spectators.count();
    }

    /** Registers a countdown that the end of the round stops. */
    public Countdown timer(Countdown countdown) {
        timers.add(countdown);
        return countdown;
    }

    /**
     * Runs a timed phase of the round on {@code bar}'s seconds: the bar shows them unless the game's config turns
     * timer bars off, {@code onSecond} hears each remaining second and {@code onEnd} the end. The end of the round
     * stops it; a phase that ends early stops it through the returned countdown.
     */
    public Countdown clock(TimerBar bar, IntConsumer onSecond, Runnable onEnd) {
        if (config.timerBar()) {
            bars.add(bar);
            bar.update(bar.seconds());
        }
        return timer(Countdown.start(plugin, bar.seconds(), remaining -> {
            if (config.timerBar()) {
                bar.update(remaining);
            }
            onSecond.accept(remaining);
        }, () -> {
            bar.hide();
            onEnd.run();
        }));
    }

    /** Whether no boss bar tells the time, so that the sidebar has to. */
    public boolean clockOnSidebar() {
        return !config.timerBar();
    }

    /** Skips the respawn screen {@code delayTicks} after a death. */
    public void respawn(Player player, long delayTicks) {
        Tasks.later(plugin, delayTicks, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    /** Every player gets {@code tracker}, {@code minutes} into the final phase. */
    public void handOutTrackers(int minutes, ItemStack tracker) {
        arena.announceTrackers(minutes);
        for (var player : players()) {
            player.getInventory().addItem(tracker.clone());
            Sounds.NOTIFY.play(player);
        }
    }

    /** Called with every remaining second of the final phase; announces its last minutes in chat. */
    public void announceEnd(int remaining) {
        if (END_ANNOUNCEMENTS.contains(remaining)) {
            arena.announceEnd(remaining / SECONDS_PER_MINUTE);
        }
    }

    /**
     * Stops the round's countdowns and bars, shows the summary and restarts the server after {@code countdown.restart};
     * the winners must be awarded before. False if the round had already ended.
     */
    public boolean end() {
        return end(config.restartSeconds());
    }

    /** As {@link #end()}, with a longer wait before the restart for a game that still has something to offer. */
    public boolean end(int restartSeconds) {
        if (!endInPlace(player -> ProxyMessaging.play(plugin, player, api.server().group()))) {
            return false;
        }
        timer(ServerRestart.start(plugin, arena, restartSeconds, summary::closeRespects, summary::returningIn));
        return true;
    }

    public boolean endInPlace(Consumer<Player> playAgain) {
        if (phase == GamePhase.ENDING) {
            return false;
        }
        enter(GamePhase.ENDING);
        stop();
        summary.show(playAgain);
        return true;
    }

    public boolean reopen(RoundSummary nextSummary) {
        if (phase != GamePhase.ENDING) {
            return false;
        }
        stop();
        summary.close();
        summary = nextSummary;
        players().forEach(summary::join);
        spectators.clearTargets();
        enter(GamePhase.LOBBY);
        return true;
    }

    public void stop() {
        timers.forEach(Countdown::cancel);
        bars.forEach(TimerBar::hide);
        timers.clear();
        bars.clear();
        if (!away.isEmpty()) {
            away.clear();
            releaseRejoins();
        }
    }

    private static List<Player> online(Set<UUID> ids) {
        return ids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }
}
