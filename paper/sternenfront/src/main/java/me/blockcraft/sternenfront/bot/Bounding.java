package me.blockcraft.sternenfront.bot;

/**
 * Bounding overwatch for a fireteam's two elements: one runs forward while the other covers it from where it stands,
 * then they swap. The element without the leader goes first. A lone element catches its breath between bounds.
 */
final class Bounding {

    static final int NONE = -1;

    private int moving = NONE;
    private long since;
    private long pausedUntil;
    private int bound;

    int moving() {
        return moving;
    }

    /** Counts the bounds, so a member can tell a new one began. */
    int bound() {
        return bound;
    }

    void reset() {
        moving = NONE;
        pausedUntil = 0;
    }

    /** {@code present} says which elements have anyone alive; {@code arrived} whether the moving one finished its bound. */
    int update(boolean[] present, boolean arrived, long tick, int boundTicks) {
        if (tick < pausedUntil) {
            return NONE;
        }
        boolean underway = moving != NONE && present[moving];
        if (underway && !arrived && tick - since < boundTicks) {
            return moving;
        }
        int next = underway ? present[1 - moving] ? 1 - moving : NONE : present[1] ? 1 : present[0] ? 0 : NONE;
        if (underway && next == NONE) {
            pausedUntil = tick + boundTicks / 2;
        }
        moving = next;
        since = tick;
        if (next != NONE) {
            bound++;
        }
        return moving;
    }
}
