package me.blockcraft.sternenfront.game;

final class ControlCycle {

    enum Phase { READY, WARNING, ACTIVE, COOLDOWN }

    private long startsAt;
    private long endsAt;
    private long readyAt;

    boolean trigger(long tick, int warning, int duration, int cooldown) {
        if (phase(tick) != Phase.READY) {
            return false;
        }
        startsAt = tick + warning;
        endsAt = startsAt + duration;
        readyAt = tick + Math.max(cooldown, warning + duration);
        return true;
    }

    Phase phase(long tick) {
        return tick < startsAt ? Phase.WARNING : tick < endsAt ? Phase.ACTIVE
                : tick < readyAt ? Phase.COOLDOWN : Phase.READY;
    }

    long remaining(long tick) {
        return Math.max(0, (phase(tick) == Phase.WARNING ? startsAt : phase(tick) == Phase.ACTIVE ? endsAt : readyAt) - tick);
    }

    long ticksUntilReady(long tick) {
        return Math.max(0, readyAt - tick);
    }
}
