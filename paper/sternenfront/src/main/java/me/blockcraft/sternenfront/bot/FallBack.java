package me.blockcraft.sternenfront.bot;

/**
 * A fireteam breaking contact. It runs back when outnumbered, or once when cut down to its leader in a fight, and
 * pushes again as soon as it is back with the odds even and its leader not alone, or when its time is up. Odds that
 * are still bad when the time is up, or turn bad again soon after it pushed, make it give the objective up.
 */
final class FallBack {

    enum Order { PUSH, FALL_BACK, GIVE_UP }

    private long until = -1;
    private long endedAt = -1;
    private boolean rallied;

    Order update(boolean outnumbered, boolean alone, boolean contact, boolean back, long tick, int ticks) {
        if (!alone) {
            rallied = false;
        }
        if (until >= 0) {
            boolean expired = tick >= until;
            if (!expired && (outnumbered || alone || !back)) {
                return Order.FALL_BACK;
            }
            until = -1;
            endedAt = expired && outnumbered ? -1 : tick;
            return expired && outnumbered ? Order.GIVE_UP : Order.PUSH;
        }
        if (outnumbered && endedAt >= 0 && tick - endedAt < ticks) {
            endedAt = -1;
            return Order.GIVE_UP;
        }
        boolean rally = alone && contact && !rallied;
        if (!outnumbered && !rally) {
            return Order.PUSH;
        }
        rallied = alone;
        until = tick + ticks;
        return Order.FALL_BACK;
    }
}
