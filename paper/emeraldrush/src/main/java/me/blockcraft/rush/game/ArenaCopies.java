package me.blockcraft.rush.game;

import me.blockcraft.api.game.RoundScope;
import me.blockcraft.api.world.MainWorldCopy;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * The worlds rounds play over the server's life: a fresh copy of the main world's map for every round, so ore a round
 * seeded or mined never reaches the next. The lobby stays where it is.
 */
public final class ArenaCopies {

    private final MainWorldCopy copy;

    public ArenaCopies(Plugin plugin) {
        this.copy = new MainWorldCopy(plugin, "emeraldrush");
    }

    /**
     * Moves the round's spawns into its world and loads the chunks the search seeds ore into, so the start does not
     * wait for them.
     */
    void prepare(RoundScope scope, Spawns spawns, int radius) {
        copy.load(scope).ifPresent(spawns::playIn);
        spawns.get(SpawnPoint.SEARCH).ifPresent(centre -> keepLoaded(scope, centre, radius));
    }

    private static void keepLoaded(Plugin owner, Location centre, int radius) {
        var world = centre.getWorld();
        for (int x = (centre.getBlockX() - radius) >> 4; x <= (centre.getBlockX() + radius) >> 4; x++) {
            for (int z = (centre.getBlockZ() - radius) >> 4; z <= (centre.getBlockZ() + radius) >> 4; z++) {
                int chunkX = x;
                int chunkZ = z;
                world.getChunkAtAsync(chunkX, chunkZ).thenRun(() -> world.addPluginChunkTicket(chunkX, chunkZ, owner));
            }
        }
    }
}
