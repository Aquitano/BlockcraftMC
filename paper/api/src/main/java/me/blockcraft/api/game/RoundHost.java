package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.world.WaitingLobby;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plays a game's rounds one after another on the same server instead of restarting it after each. Every round is a
 * fresh game from the factory, built against its own {@link RoundScope}, so it starts from nothing the last one
 * left: the scope takes the last round's listeners, tasks, entities and worlds with it, and the players still here
 * are reset as if they had just joined and seated in the new round. {@code rounds-in-place} rounds in a row play
 * this way; after them, or when a round left something registered under the plugin itself, the server restarts as
 * it always did.
 */
public final class RoundHost<G extends RoundHost.Game> implements Listener {

    /** A game the host builds once per round. */
    public interface Game extends RoundConnections.Seating {

        LobbyCountdown lobbyCountdown();

        /** Ends what the round holds beyond its scope, such as statistics still to be written. */
        default void close() {
        }

        /** The players of the last round have just been seated in this one and its lobby hurried for them. */
        default void reopened() {
        }
    }

    /**
     * What the plugin itself holds at the end of a round, outside every round's scope. A round that grows it left
     * something behind that only a restart clears.
     */
    record Footprint(int listeners, int tasks, List<NamespacedKey> worlds) {

        /** Pending one-shot tasks of the plugin come and go, so a little growth there is not a leak. */
        static final int TASK_SLACK = 2;

        List<String> grownSince(Footprint baseline) {
            var problems = new ArrayList<String>();
            if (listeners > baseline.listeners) {
                problems.add((listeners - baseline.listeners) + " listeners");
            }
            if (tasks > baseline.tasks + TASK_SLACK) {
                problems.add((tasks - baseline.tasks) + " tasks");
            }
            if (!worlds.equals(baseline.worlds)) {
                problems.add("worlds " + worlds + " instead of " + baseline.worlds);
            }
            return problems;
        }
    }

    private static final int MINIMUM_SECONDS = 5;
    private static final long RESTART_DELAY_TICKS = 20L;
    private static final StackWalker STACK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final List<Class<? extends Entity>> DEBRIS = List.of(Item.class, ExperienceOrb.class, Projectile.class,
            TNTPrimed.class, FallingBlock.class, AreaEffectCloud.class, Boat.class, Minecart.class);
    private static final int ANNOUNCE_FROM = 5;

    private final JavaPlugin plugin;
    private final Function<RoundScope, G> factory;
    private final int roundsInPlace;
    private final int reopenSeconds;
    private final PlayerBaseline baseline = new PlayerBaseline();
    private RoundScope scope;
    private G game;
    private int rounds = 1;
    private Optional<Footprint> firstFootprint = Optional.empty();

    private RoundHost(JavaPlugin plugin, GameConfig config, Function<RoundScope, G> factory) {
        this.plugin = plugin;
        this.roundsInPlace = config.roundsInPlace();
        this.reopenSeconds = config.reopenSeconds();
        this.factory = factory;
    }

    /**
     * Builds the first round. Whatever the game needs for the server's whole life the plugin builds before, outside
     * the factory; everything a round owns, its listeners included, the factory registers through the scope.
     */
    public static <G extends Game> RoundHost<G> start(JavaPlugin plugin, GameConfig config, Function<RoundScope, G> factory) {
        var host = new RoundHost<>(plugin, config, factory);
        plugin.getServer().getPluginManager().registerEvents(host, plugin);
        host.open();
        return host;
    }

    public G current() {
        return game;
    }

    private void open() {
        scope = new RoundScope(plugin, this);
        game = factory.apply(scope);
    }

    /** Whether the round of {@code ending} is followed by another on this server rather than by a restart. */
    boolean playsNext(RoundScope ending) {
        if (ending != scope || rounds >= roundsInPlace || scope.restartsAfter()) {
            return false;
        }
        var now = footprint();
        if (firstFootprint.isEmpty()) {
            firstFootprint = Optional.of(now);
            return true;
        }
        var grown = now.grownSince(firstFootprint.get());
        if (!grown.isEmpty()) {
            plugin.getSLF4JLogger().warn("Round {} left {} behind outside its scope; restarting instead of playing on", rounds, grown);
            return false;
        }
        return true;
    }

    private Footprint footprint() {
        int listeners = HandlerList.getRegisteredListeners(plugin).size();
        int tasks = (int) Bukkit.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == plugin && task.isSync())
                .count();
        var worlds = Bukkit.getWorlds().stream()
                .filter(world -> !scope.owns(world))
                .map(World::getKey)
                .sorted(Comparator.comparing(NamespacedKey::asString))
                .collect(Collectors.toUnmodifiableList());
        return new Footprint(listeners, tasks, worlds);
    }

    /** Counts down to the next round, which follows once {@code beforeLeaving} has run. */
    Countdown nextRoundIn(Arena arena, int seconds, Runnable beforeLeaving) {
        int duration = Math.max(MINIMUM_SECONDS, seconds);
        arena.announceNextRound(duration);
        return Countdown.start(scope, duration, remaining -> {
            if (remaining >= 1 && remaining <= ANNOUNCE_FROM) {
                arena.announceNextRound(remaining);
            }
            if (remaining == 1) {
                beforeLeaving.run();
            }
        }, () -> Bukkit.getScheduler().runTask(plugin, this::next));
    }

    /**
     * Closes the round that ended and opens the next with everyone still here. A round world that does not unload,
     * or a next round that fails to open, would leave the server between rounds, so it restarts instead.
     */
    private void next() {
        var players = List.copyOf(Bukkit.getOnlinePlayers());
        var away = away();
        for (var player : players) {
            if (player.isDead()) {
                player.spigot().respawn();
            }
            player.leaveVehicle();
            player.eject();
            if (scope.owns(player.getWorld())) {
                player.teleport(away);
            }
        }
        if (!close()) {
            restart("a round world stayed loaded");
            return;
        }
        var api = BlockcraftApi.get();
        try {
            for (var player : players) {
                Spectators.unwatch(player.getUniqueId());
                api.tabTeams().clearColor(player);
                baseline.restore(player, GameMode.ADVENTURE);
            }
            rounds++;
            open();
            api.presence().state(GamePhase.LOBBY.serverState());
            players.stream().filter(Player::isOnline).forEach(game::join);
            game.lobbyCountdown().hurry(reopenSeconds);
            game.reopened();
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not open the next round", e);
            restart("the next round did not open");
        }
    }

    private void restart(String reason) {
        plugin.getSLF4JLogger().warn("Restarting instead of playing on: {}", reason);
        Bukkit.getOnlinePlayers().forEach(player -> ProxyMessaging.hub(plugin, player));
        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, RESTART_DELAY_TICKS);
    }

    private static Location away() {
        return BlockcraftApi.get().waitingLobby().map(WaitingLobby::spawn)
                .orElseGet(() -> Bukkit.getWorlds().getFirst().getSpawnLocation());
    }

    /** Closes the round under way, as when the server stops; false if one of its worlds stayed loaded. */
    public boolean close() {
        try {
            game.close();
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().warn("Closing the round's game failed", e);
        }
        return scope.close(away());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        track(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicle(VehicleCreateEvent event) {
        track(event.getVehicle());
    }

    /**
     * What play leaves lying around, and whatever the game's own code spawns, directly or through the API, belongs to
     * the round. Other plugins' entities do not, such as the waiting island's scenery and creatures, which the API
     * spawns on its own schedule.
     */
    private void track(Entity entity) {
        if (scope != null && (DEBRIS.stream().anyMatch(type -> type.isInstance(entity)) || spawnedByGame())) {
            scope.spawned(entity);
        }
    }

    private boolean spawnedByGame() {
        var loader = plugin.getClass().getClassLoader();
        return STACK.walk(frames -> frames.anyMatch(frame -> frame.getDeclaringClass().getClassLoader() == loader));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        baseline.remember(event.getPlayer());
    }
}
