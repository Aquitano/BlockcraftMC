package me.blockcraft.sternenfront.bot;

final class BotRecovery {

    private long outsideSince = -1;

    boolean expired(boolean outside, long tick, int graceTicks) {
        if (!outside) {
            clear();
            return false;
        }
        if (outsideSince < 0) {
            outsideSince = tick;
        }
        return tick - outsideSince >= graceTicks;
    }

    void clear() {
        outsideSince = -1;
    }
}
