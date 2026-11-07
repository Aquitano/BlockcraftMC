package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

/** How long a bot has stood within {@code REACH} blocks of one spot, so one that camps near the enemy can be moved on. */
final class Stillness {

    static final double REACH = 1.5;

    private Vector spot;
    private long since;

    long update(Vector here, long tick) {
        if (spot == null || spot.distanceSquared(here) > REACH * REACH) {
            spot = here.clone();
            since = tick;
        }
        return tick - since;
    }

    void clear() {
        spot = null;
    }
}
