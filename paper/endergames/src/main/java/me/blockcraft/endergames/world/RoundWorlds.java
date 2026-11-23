package me.blockcraft.endergames.world;

import me.blockcraft.api.game.RoundScope;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.endergames.config.EnderGamesConfig.WorldSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Where the rounds of this server play: every round in a world of its own, which it throws away when it closes.
 * Generating and loading a world is the slowest part of getting a round ready, so the next round's world starts
 * generating as soon as a round ends, while its players still see the summary, rather than while the round runs,
 * which would hold two loaded worlds in memory throughout. Two dimensions take turns, since the next world loads
 * while the last one still stands. A round's world is unloaded as the round closes and its folder deleted in the
 * background, which deleting a generated world's region files on the main thread would stall; its dimension is
 * used again only once that is done.
 */
public final class RoundWorlds {

    private static final List<NamespacedKey> KEYS = List.of(
            new NamespacedKey("endergames", "runde"),
            new NamespacedKey("endergames", "runde-2"));

    private final Plugin plugin;
    private final WorldSettings settings;
    private final int startChests;
    private final Map<NamespacedKey, CompletableFuture<Void>> deleting = new HashMap<>();
    private Optional<RoundWorld> next = Optional.empty();
    private int generated;

    public RoundWorlds(Plugin plugin, WorldSettings settings, int startChests) {
        this.plugin = plugin;
        this.settings = settings;
        this.startChests = startChests;
    }

    /** The world of the round {@code round}, owned by it: the one generated for it, or else one generated now. */
    public RoundWorld take(RoundScope round) {
        var world = next.orElseGet(this::generate);
        next = Optional.empty();
        round.own(world.world(), unloaded -> deleting.put(unloaded.getKey(), Worlds.deleteInBackground(unloaded.getKey())));
        return world;
    }

    /** Starts generating the world of the round after the one that just ended. */
    public void generateNext() {
        if (next.isPresent()) {
            return;
        }
        var deletion = deleting.get(nextKey());
        if (deletion != null && !deletion.isDone()) {
            deletion.whenComplete((done, failure) -> Tasks.sync(plugin, this::generateNext));
            return;
        }
        next = Optional.of(generate());
    }

    /** A world generated for a round that never opened goes with the server. */
    public void discardNext() {
        next.ifPresent(RoundWorld::discard);
        next = Optional.empty();
    }

    /**
     * A dimension whose delete is still running is not generated again: waiting for the disk here would hold up the
     * main thread, so the round that needs it fails to open and the host restarts the server instead.
     */
    private RoundWorld generate() {
        var key = nextKey();
        var deletion = deleting.get(key);
        if (deletion != null && !deletion.isDone()) {
            throw new IllegalStateException("The EnderGames world " + key + " is still being deleted");
        }
        deleting.remove(key);
        generated++;
        var world = RoundWorld.create(plugin, settings, key);
        world.prepare(startChests);
        return world;
    }

    private NamespacedKey nextKey() {
        return KEYS.get(generated % KEYS.size());
    }
}
