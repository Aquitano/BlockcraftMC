package me.blockcraft.endergames.world;

import me.blockcraft.api.game.RoundScope;
import me.blockcraft.endergames.config.EnderGamesConfig.WorldSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * Where the rounds of this server play: every round in a world of its own, which it throws away when it closes.
 * Generating and loading a world is the slowest part of getting a round ready, so the next round's world starts
 * generating as soon as a round ends, while its players still see the summary, rather than while the round runs,
 * which would hold two loaded worlds in memory throughout. Two dimensions take turns, since the next world loads
 * while the last one still stands.
 */
public final class RoundWorlds {

    private static final List<NamespacedKey> KEYS = List.of(
            new NamespacedKey("endergames", "runde"),
            new NamespacedKey("endergames", "runde-2"));

    private final Plugin plugin;
    private final WorldSettings settings;
    private final int startChests;
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
        round.own(world.world(), unloaded -> world.discard());
        return world;
    }

    /** Starts generating the world of the round after the one that just ended. */
    public void generateNext() {
        if (next.isEmpty()) {
            next = Optional.of(generate());
        }
    }

    /** A world generated for a round that never opened goes with the server. */
    public void discardNext() {
        next.ifPresent(RoundWorld::discard);
        next = Optional.empty();
    }

    private RoundWorld generate() {
        var world = RoundWorld.create(plugin, settings, KEYS.get(generated++ % KEYS.size()));
        world.prepare(startChests);
        return world;
    }
}
