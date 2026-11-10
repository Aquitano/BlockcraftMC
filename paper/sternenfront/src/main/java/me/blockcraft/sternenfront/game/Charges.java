package me.blockcraft.sternenfront.game;

/**
 * A class ability's uses: up to {@code capacity} held at once, refilled one at a time {@code rechargeTicks} apart. With
 * one charge it is a plain cooldown.
 */
public final class Charges {

    private final int capacity;
    private final int rechargeTicks;
    private int stored;
    private long refillAt;

    public Charges(int capacity, int rechargeTicks) {
        this.capacity = Math.max(1, capacity);
        this.rechargeTicks = Math.max(1, rechargeTicks);
        stored = this.capacity;
    }

    /** Spends a charge at {@code tick}; false when none is left. */
    public boolean use(long tick) {
        refill(tick);
        if (stored == 0) {
            return false;
        }
        if (stored == capacity) {
            refillAt = tick + rechargeTicks;
        }
        stored--;
        return true;
    }

    public int stored(long tick) {
        refill(tick);
        return stored;
    }

    public int capacity() {
        return capacity;
    }

    /** Ticks until the next charge is back, or 0 when all are in. */
    public int ticksToNext(long tick) {
        refill(tick);
        return stored == capacity ? 0 : (int) (refillAt - tick);
    }

    private void refill(long tick) {
        while (stored < capacity && tick >= refillAt) {
            stored++;
            refillAt += rechargeTicks;
        }
    }
}
