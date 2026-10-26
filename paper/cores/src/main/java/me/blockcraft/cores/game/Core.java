package me.blockcraft.cores.game;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** One beacon a team defends; {@code left} as seen from the team's spawn, looking towards the enemy. */
public final class Core {

    private final Side side;
    private final boolean left;
    private final Block block;
    private boolean destroyed;
    private long alarmUntil;
    private long lastAlarmMessage;
    private boolean announced;

    public Core(Side side, boolean left, Block block) {
        this.side = side;
        this.left = left;
        this.block = block;
    }

    public Side side() {
        return side;
    }

    public String key() {
        return left ? "left" : "right";
    }

    public Block block() {
        return block;
    }

    public Location center() {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    void destroy() {
        destroyed = true;
    }

    public boolean isUnderAttack(long now) {
        return !destroyed && announced && now - alarmUntil < 0;
    }

    /** Keeps the alarm on for a while; true when the team should hear it in chat again. */
    boolean sound(long now, long holdNanos, long chatNanos) {
        alarmUntil = now + holdNanos;
        if (announced && now - lastAlarmMessage < chatNanos) {
            return false;
        }
        announced = true;
        lastAlarmMessage = now;
        return true;
    }
}
