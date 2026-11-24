package me.blockcraft.sternenfront.secret;

import java.util.List;

/**
 * One runner on the Kessel-Lauf. Crossing the start gate starts the clock, every checkpoint must follow in order and
 * the finish stops it. Leaving the course, which includes falling into the pit, or running longer than the limit ends
 * the attempt; crossing the start gate again starts afresh. Gates are planes across the course at the given x, in the
 * order they are run; the course may run either way.
 */
final class KesselRun {

    enum Step { NONE, STARTED, CHECKPOINT, FINISHED, FAILED }

    private static final int IDLE = 0;

    private final List<Double> gates;
    private final double direction;
    private final long limitTicks;
    private int next = IDLE;
    private long startTick;
    private long result;

    KesselRun(List<Double> gates, long limitTicks) {
        if (gates.size() < 2) {
            throw new IllegalArgumentException("A course needs a start and a finish");
        }
        this.gates = List.copyOf(gates);
        this.direction = Math.signum(gates.getLast() - gates.getFirst());
        this.limitTicks = limitTicks;
    }

    /** The runner moved from {@code fromX} to {@code toX} at {@code tick}, ending on the course or not. */
    Step move(double fromX, double toX, boolean onCourse, long tick) {
        if (!onCourse) {
            return interrupt();
        }
        if (crosses(gates.getFirst(), fromX, toX)) {
            next = 1;
            startTick = tick;
            return Step.STARTED;
        }
        if (next == IDLE) {
            return Step.NONE;
        }
        if (tick - startTick > limitTicks) {
            return interrupt();
        }
        if (!crosses(gates.get(next), fromX, toX)) {
            return Step.NONE;
        }
        next++;
        if (next < gates.size()) {
            return Step.CHECKPOINT;
        }
        result = tick - startTick;
        next = IDLE;
        return Step.FINISHED;
    }

    Step interrupt() {
        boolean running = running();
        next = IDLE;
        return running ? Step.FAILED : Step.NONE;
    }

    private boolean crosses(double gate, double fromX, double toX) {
        return (fromX - gate) * direction < 0 && (toX - gate) * direction >= 0;
    }

    boolean running() {
        return next != IDLE;
    }

    /** Gates passed so far in a running attempt, the start included. */
    int passed() {
        return next;
    }

    int gates() {
        return gates.size();
    }

    long elapsed(long tick) {
        return tick - startTick;
    }

    /** The ticks of the last finished run. */
    long result() {
        return result;
    }
}
