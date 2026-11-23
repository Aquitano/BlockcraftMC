package me.blockcraft.bedwars.game;

import me.blockcraft.api.world.MapCopies;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.bedwars.map.MapPool;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;

/**
 * The maps' worlds over the server's life. A map is copied from the pool in the background the first time a vote
 * picks it, since what its folder holds from before the server started may have been played on. A round's world is
 * discarded when the round closes and copied anew in the background, so that a later round finds it as it was.
 */
public final class ArenaCopies {

    private final Plugin plugin;
    private final MapPool pool;
    private final TemplateDimensions arenas;
    private final MapCopies copies;
    private final Set<String> copied = new HashSet<>();

    public ArenaCopies(Plugin plugin, MapPool pool, TemplateDimensions arenas) {
        this.plugin = plugin;
        this.pool = pool;
        this.arenas = arenas;
        this.copies = new MapCopies(plugin, arenas);
    }

    /** Whether no round or builder has the map's world loaded, so that a round may still pick it. */
    boolean free(ArenaMap map) {
        return arenas.loaded(map.key()).isEmpty();
    }

    /** Copies the map into place in the background, unless a fresh copy is there or on its way. */
    void prepare(ArenaMap map) {
        if (copied.add(map.key())) {
            copies.copyInBackground(pool.world(map.key()), map.key());
        }
    }

    /** The map's fresh world, once its copy is done. */
    World load(ArenaMap map) {
        if (!free(map)) {
            throw new IllegalStateException("The map is already loaded: " + map.key());
        }
        prepare(map);
        return copies.load(map.key());
    }

    /**
     * Copies the unloaded world of a round anew. A stopping server can schedule no copy and needs none, since its
     * next start copies every map before playing it.
     */
    void discard(World world) {
        if (plugin.isEnabled()) {
            copies.recopy(world);
        }
    }
}
