package me.blockcraft.lobby.activity;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Where something crossing the sky on a straight line can fly without passing through the map. */
final class Airspace {

    private static final int ROUTES = 8;

    private Airspace() {
    }

    /**
     * The start of the lowest of a few random straight routes {@code length} blocks long across {@code over}, at least
     * {@code height} above it and {@code margin} above every block within {@code halfWidth} of the route, shifted up
     * to {@code aside} blocks sideways. Its direction is the heading.
     */
    static Location lowestRoute(Location over, double height, double length, double halfWidth, double margin, double aside) {
        var random = ThreadLocalRandom.current();
        Location best = null;
        for (int i = 0; i < ROUTES; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            var direction = new Vector(Math.cos(angle), 0, Math.sin(angle));
            var shift = new Vector(-direction.getZ(), 0, direction.getX()).multiply(aside > 0 ? random.nextDouble(-aside, aside) : 0);
            var start = over.clone().add(direction.clone().multiply(-length / 2)).add(shift).add(0, height, 0);
            start.setY(Math.max(start.getY(), clearAbove(start, direction, length, halfWidth) + margin));
            start.setDirection(direction);
            if (best == null || start.getY() < best.getY()) {
                best = start;
            }
        }
        return best;
    }

    /** Unloaded chunks count as clear: no one is near enough to see. */
    private static int clearAbove(Location start, Vector direction, double length, double halfWidth) {
        var world = start.getWorld();
        var side = new Vector(-direction.getZ(), 0, direction.getX());
        int clear = world.getMinHeight();
        for (double along = -halfWidth; along <= length + halfWidth; along++) {
            for (double across = -halfWidth; across <= halfWidth; across++) {
                int x = (int) Math.floor(start.getX() + direction.getX() * along + side.getX() * across);
                int z = (int) Math.floor(start.getZ() + direction.getZ() * along + side.getZ() * across);
                if (world.isChunkLoaded(x >> 4, z >> 4)) {
                    clear = Math.max(clear, world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1);
                }
            }
        }
        return clear;
    }
}
