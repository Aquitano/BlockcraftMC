package me.blockcraft.sternenfront.combat;

/** The sandstorm's wind over time: steady between gusts, swelling and falling every {@code periodTicks}. */
record StormWind(long startedAt, int periodTicks, int gustTicks, double boost) {

    StormWind {
        periodTicks = Math.max(1, periodTicks);
        gustTicks = Math.clamp(gustTicks, 1, periodTicks);
    }

    /** 0 between gusts, rising to 1 in the middle of one. */
    double gust(long tick) {
        long phase = Math.floorMod(tick - startedAt, (long) periodTicks);
        return phase < gustTicks ? Math.sin(Math.PI * phase / gustTicks) : 0;
    }

    boolean gustStarts(long tick) {
        return Math.floorMod(tick - startedAt, (long) periodTicks) == 0;
    }

    double strength(long tick) {
        return 1 + boost * gust(tick);
    }

    int particles(long tick, int base) {
        return (int) Math.round(base * strength(tick));
    }
}
