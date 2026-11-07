package me.blockcraft.sternenfront.game;

import java.util.List;
import java.util.OptionalInt;

final class AbilityHints {

    private List<String> keys = List.of();
    private long started = -1;
    private boolean complete;

    List<String> keys() {
        return keys;
    }

    boolean start(List<String> keys, long tick) {
        boolean changed = !this.keys.equals(keys);
        if (changed) {
            this.keys = List.copyOf(keys);
            complete = false;
        }
        if (changed || started < 0 && !complete) started = tick;
        return changed;
    }

    OptionalInt index(long tick, int ticksPerHint) {
        if (complete || started < 0) return OptionalInt.empty();
        if (ticksPerHint <= 0 || tick - started >= (long) ticksPerHint * keys.size()) {
            complete = true;
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) ((tick - started) / ticksPerHint));
    }

    void interrupt() {
        started = -1;
    }
}
