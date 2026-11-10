package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

/**
 * How long someone has stood within {@code reach} blocks of one spot: a bot camping near the enemy, to move it on, or a
 * player standing idle, whom no fireteam follows.
 */
final class Stillness {

    static final double REACH = 1.5;

    private final double reach;
    private Vector spot;
    private long since;

    Stillness(double reach) {
        this.reach = reach;
    }

    long update(Vector here, long tick) {
        if (spot == null || spot.distanceSquared(here) > reach * reach) {
            spot = here.clone();
            since = tick;
        }
        return tick - since;
    }

    void clear() {
        spot = null;
    }
}
