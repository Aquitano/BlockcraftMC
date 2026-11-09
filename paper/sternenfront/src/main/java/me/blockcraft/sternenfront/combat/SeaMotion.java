package me.blockcraft.sternenfront.combat;

record SeaMotion(int periodTicks, int breakTicks, double reach) {

    double distance(long tick, int offset) {
        long phase = Math.floorMod(tick + offset, (long) periodTicks);
        return reach * Math.max(0, 1 - (double) phase / (periodTicks - breakTicks));
    }

    double impact(long tick, int offset) {
        long phase = Math.floorMod(tick + offset, (long) periodTicks);
        long breaking = phase - (periodTicks - breakTicks);
        return breaking < 0 ? 0 : Math.sin(Math.PI * breaking / breakTicks);
    }
}
