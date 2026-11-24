package me.blockcraft.sternenfront.game;

/**
 * A blaster's trigger, read from what a 26.3 client sends. A left click sends one swing and nothing while it is held,
 * except on a block the player may break: there it starts breaking the block, swings every tick and aborts on
 * release. {@link TriggerCells} keeps such a block around the player's eyes, so the three calls here are a start, a
 * swing and an abort in the order the client sent them.
 * <p>
 * The client aborts and starts again without the button moving whenever the block or the held item changes, so a start
 * right after an abort carries the hold on and is no new pull. A swing with no start before it is a click that met
 * no such block, which is a pull and its release at once.
 * <p>
 * A client that finds the block it last broke under a button it already holds goes on swinging with no start, and then
 * sends no abort either. Such a hold shows only as a swing every tick, each of them a pull, and ends with the swings;
 * {@link #unstarted} asks for the change of item that makes the client start properly.
 */
public final class Trigger {

    /** An abort and the start that follows it in the same client tick reach the server at most this far apart. */
    private static final int RESTART_TICKS = 1;
    /** A held trigger swings every client tick; this long without one, it no longer counts as held. */
    private static final int SILENT_TICKS = 5;
    private static final int ENGAGED_TICKS = 10;
    private static final long LONG_AGO = Long.MIN_VALUE / 2;

    private enum State { UP, DOWN, LETTING_GO }

    private State state = State.UP;
    private long lastSwing = LONG_AGO;
    private long letGoAt = LONG_AGO;
    private long tappedAt = LONG_AGO;
    private int taps;
    private boolean askedToStart;

    /** The client began breaking a trigger block; true when that is a fresh pull. */
    public boolean started(long tick) {
        boolean carriedOn = state == State.DOWN || state == State.LETTING_GO && tick - letGoAt <= RESTART_TICKS
                || tick - tappedAt <= RESTART_TICKS;
        state = State.DOWN;
        lastSwing = tick;
        taps = 0;
        return !carriedOn;
    }

    /**
     * The client swung; true when that is a click on its own, pulled and released at once. A swing right after an
     * abort belongs to the hold that the abort seemed to end.
     */
    public boolean swung(long tick) {
        if (state == State.DOWN) {
            lastSwing = tick;
            return false;
        }
        boolean carriedOn = state == State.LETTING_GO && tick - letGoAt <= RESTART_TICKS;
        state = State.UP;
        if (tick - tappedAt > RESTART_TICKS) {
            taps = carriedOn ? 1 : 0;
            askedToStart = false;
        }
        taps++;
        tappedAt = tick;
        return !carriedOn;
    }

    public void stopped(long tick) {
        if (state == State.DOWN) {
            state = State.LETTING_GO;
            letGoAt = tick;
        }
    }

    public void tick(long tick) {
        if (state == State.LETTING_GO && tick - letGoAt > RESTART_TICKS) {
            state = State.UP;
        }
    }

    public boolean held(long tick) {
        return state == State.DOWN && tick - lastSwing <= SILENT_TICKS || swingingOn(tick);
    }

    private boolean swingingOn(long tick) {
        return state != State.DOWN && taps > 1 && tick - tappedAt <= RESTART_TICKS;
    }

    /** True once for a hold that goes on with no start. */
    public boolean unstarted(long tick) {
        if (askedToStart || !swingingOn(tick)) {
            return false;
        }
        askedToStart = true;
        return true;
    }

    /** Held, or clicked so recently that the hold may only not have reached the server yet. */
    public boolean engaged(long tick) {
        return held(tick) || state == State.LETTING_GO || tick - tappedAt <= ENGAGED_TICKS;
    }

    public void release() {
        state = State.UP;
    }
}
