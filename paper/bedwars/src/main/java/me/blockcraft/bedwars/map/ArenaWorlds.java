package me.blockcraft.bedwars.map;

import me.blockcraft.api.world.WorldTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Since 26.2 every world of a server is a dimension of its one level, so a pool map is copied to
 * {@code <level>/dimensions/bedwars/<key>} before it loads as the world {@code bedwars:<key>}. Nothing generates
 * around a map: what lies beyond its build is void, as the players fall into.
 */
public final class ArenaWorlds {

    private static final String NAMESPACE = "bedwars";

    private ArenaWorlds() {
    }

    public static NamespacedKey key(String map) {
        return new NamespacedKey(NAMESPACE, map);
    }

    /** The world of {@code key} if it is loaded, so a builder's edit and a round never load a map twice. */
    public static Optional<World> loaded(String map) {
        return Optional.ofNullable(Bukkit.getWorld(key(map)));
    }

    /** Blocking file work, run before {@link #load}; the copy replaces whatever an earlier load left there. */
    public static void copy(MapPool pool, String map) {
        WorldTemplate.at(pool.world(map)).restore(folder(map));
    }

    public static World load(String map) {
        return loaded(map).orElseGet(() -> {
            var world = WorldCreator.ofKey(key(map))
                    .generator(new ChunkGenerator() { })
                    .createWorld();
            if (world == null) {
                throw new IllegalStateException("Could not load the map " + map);
            }
            world.setAutoSave(false);
            settle(world);
            return world;
        });
    }

    /** What every BedWars world keeps still: the time of day, the weather, growth and the locator bar. */
    public static void settle(World world) {
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
    }

    /** Flushes the map a builder edited and copies it back into the pool. */
    public static void publish(MapPool pool, World world, String map) {
        world.save(true);
        WorldTemplate.at(pool.world(map)).publish(folder(map));
    }

    private static Path folder(String map) {
        var level = Bukkit.getWorlds().getFirst().getName();
        return Bukkit.getWorldContainer().toPath().resolve(level).resolve("dimensions").resolve(NAMESPACE).resolve(map);
    }
}
