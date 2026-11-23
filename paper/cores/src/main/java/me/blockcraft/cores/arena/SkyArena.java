package me.blockcraft.cores.arena;

import me.blockcraft.api.game.LootChests;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.api.game.RoundScope;
import me.blockcraft.api.world.MapCopies;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * Himmelsfeste over the server's life. It is built once at the start and kept in {@code cores-built-maps/} of the
 * server as a template. Every round plays a fresh copy that goes with the round. Two copies take turns, each made anew
 * in the background while the other is played: a copy begun as a round closes would only start on the next tick,
 * after the next round needed it. Without a template, or when a copy fails, the round plays a map built anew and the
 * server restarts after it.
 */
public final class SkyArena {

    private static final String TEMPLATES = "cores-built-maps";
    private static final List<String> COPIES = List.of(SkyMap.KEY, SkyMap.KEY + "-2");

    private final Plugin plugin;
    private final TemplateDimensions dimensions;
    private final MapCopies copies;
    private final LootTable loot;
    private final Optional<Location> lobby;
    private final boolean kept;
    private int turn;

    private SkyArena(Plugin plugin, TemplateDimensions dimensions, MapCopies copies, LootTable loot,
                     Optional<Location> lobby, boolean kept) {
        this.plugin = plugin;
        this.dimensions = dimensions;
        this.copies = copies;
        this.loot = loot;
        this.lobby = lobby;
        this.kept = kept;
    }

    /** Builds the map; {@code lobby} is the shared waiting lobby, without which the map brings its own. */
    public static SkyArena build(Plugin plugin, LootTable loot, Optional<Location> lobby) {
        var dimensions = new TemplateDimensions("cores", world -> {
            Worlds.settle(world);
            playable(world);
        });
        var copies = new MapCopies(plugin, dimensions);
        var world = built(dimensions, COPIES.getFirst(), lobby);
        var template = Bukkit.getWorldContainer().toPath().resolve(TEMPLATES).resolve(SkyMap.KEY);
        boolean kept = true;
        try {
            copies.keep(world, template);
            copies.copyInBackground(template, COPIES.getLast());
        } catch (UncheckedIOException e) {
            plugin.getSLF4JLogger().warn("Could not keep {} as a template, so the server restarts after every round", SkyMap.NAME, e);
            kept = false;
        }
        return new SkyArena(plugin, dimensions, copies, loot, lobby, kept);
    }

    /** A pristine map the round owns, with cores of its own and its chests rolled anew. */
    public CoresMap open(RoundScope scope) {
        if (!kept) {
            scope.restartAfter();
        }
        var key = COPIES.get(turn);
        turn = (turn + 1) % COPIES.size();
        World world;
        try {
            world = copies.load(key);
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().warn("Could not load the copy {} of {}; building it anew and restarting after the round", key, SkyMap.NAME, e);
            scope.restartAfter();
            world = built(dimensions, key, lobby);
        }
        var map = SkyMap.in(scope.own(world, this::discard), lobby);
        map.fillChests(new LootChests(loot));
        map.lobbySpawn().getWorld().setSpawnLocation(map.lobbySpawn());
        return map;
    }

    private static World built(TemplateDimensions dimensions, String name, Optional<Location> lobby) {
        var key = dimensions.key(name);
        Worlds.discard(key);
        var world = Worlds.createArena(key);
        playable(world);
        SkyMap.build(world, lobby);
        return world;
    }

    /** Hunger and natural regeneration are part of the fight; no mob spawns anyway. */
    private static void playable(World world) {
        world.setDifficulty(Difficulty.NORMAL);
    }

    /** A stopping server can schedule no copy and needs none, since its next start builds the map anew. */
    private void discard(World world) {
        if (plugin.isEnabled()) {
            copies.recopy(world);
        }
    }
}
