package me.blockcraft.zombies.game;

public final class Ammo {

    private final int capacity;
    private final int reserveCapacity;
    private int clip;
    private int reserve;
    private int readyAt;
    private int reloadFrom;
    private int reloadAt;
    private boolean upgraded;

    public Ammo(int capacity, int reserveCapacity) {
        this.capacity = capacity;
        this.reserveCapacity = reserveCapacity;
        refill();
    }

    public int clip() { return clip; }
    public int capacity() { return capacity; }
    public int reserve() { return reserve; }
    public boolean upgraded() { return upgraded; }
    public boolean reloading() { return reloadAt > 0; }
    public int reloadAt() { return reloadAt; }
    public boolean full() { return clip == capacity && reserve == reserveCapacity; }

    /** How much of a running reload has passed by {@code tick}, from none to all of it. */
    public double reloaded(int tick) {
        return reloading() ? Math.clamp((double) (tick - reloadFrom) / (reloadAt - reloadFrom), 0, 1) : 0;
    }

    public boolean shoot(int tick, int cooldown) {
        if (reloading() || tick < readyAt || clip == 0) {
            return false;
        }
        clip--;
        readyAt = tick + cooldown;
        return true;
    }

    public boolean reload(int tick, int duration) {
        if (reloading() || clip == capacity || reserve == 0) {
            return false;
        }
        reloadFrom = tick;
        reloadAt = tick + Math.max(1, duration);
        return true;
    }

    public boolean tick(int tick) {
        if (!reloading() || tick < reloadAt) {
            return false;
        }
        int transferred = Math.min(capacity - clip, reserve);
        clip += transferred;
        reserve -= transferred;
        reloadAt = 0;
        return true;
    }

    public void cancelReload() {
        reloadAt = 0;
    }

    public void refill() {
        clip = capacity;
        reserve = reserveCapacity;
        reloadAt = 0;
    }

    public void upgrade() {
        upgraded = true;
        refill();
    }
}
