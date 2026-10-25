package me.blockcraft.zombies.game;

public final class Ammo {

    private final int capacity;
    private final int reserveCapacity;
    private int clip;
    private int reserve;
    private int readyAt;
    private int reloadAt;
    private boolean upgraded;

    public Ammo(int capacity, int reserveCapacity) {
        this.capacity = capacity;
        this.reserveCapacity = reserveCapacity;
        refill();
    }

    public int clip() { return clip; }
    public int reserve() { return reserve; }
    public boolean upgraded() { return upgraded; }
    public boolean reloading() { return reloadAt > 0; }
    public int reloadAt() { return reloadAt; }
    public boolean full() { return clip == capacity && reserve == reserveCapacity; }

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
