package me.blockcraft.sternenfront.bot;

/**
 * A fireteam cut down to its leader in a fight pulls back and waits for the others to come up, rather than pushing on
 * alone. It waits once each time it is left alone, for at most the time it is given, so a team nobody joins goes on.
 */
final class Rally {

    private long until = -1;

    boolean update(boolean alone, boolean contact, boolean assaulting, long tick, int ticks) {
        if (!alone || assaulting) {
            until = -1;
            return false;
        }
        if (until < 0 && contact) {
            until = tick + ticks;
        }
        return tick < until;
    }
}
