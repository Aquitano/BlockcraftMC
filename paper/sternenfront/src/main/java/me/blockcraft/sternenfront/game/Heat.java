package me.blockcraft.sternenfront.game;

import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * A blaster's heat instead of ammunition: every shot heats it, a pause cools it, and a gun that reaches full heat
 * vents for {@link Rules#ventTicks} before it fires again, cooling from full to nothing meanwhile. A fresh pull of the
 * trigger while the cooling heat is inside the vent's window ends the vent at once; one outside it lengthens the vent.
 */
public final class Heat {

    public enum Trigger { FIRED, OVERHEATED, TOO_SOON, VENTING }

    public enum VentCatch { CAUGHT, MISSED, NOT_COUNTED }

    /** {@code windowShare} of the meter is the vent's window and a miss adds {@code missPenaltyShare} of the vent. */
    public record Rules(int coolDelayTicks, double coolPerTick, int ventTicks, double windowShare, double missPenaltyShare) {
    }

    /**
     * The meter cells, as 0-based indices, from {@code low} up to, not including, {@code high}; a pull catches the window
     * while the last lit cell is one of them.
     */
    public record Window(int low, int high) {
    }

    /** The meter shows heat in these cells, and a window covers whole cells so the HUD draws exactly what a pull must hit. */
    public static final int METER_CELLS = 10;
    /** A held trigger pulls every 4 ticks, and lag can stretch one gap to 7, so a pull this long after the last is fresh. */
    private static final int RELEASED_AFTER_TICKS = 8;
    private static final double FULL = 100;
    private static final long LONG_AGO = Long.MIN_VALUE / 2;

    private final Rules rules;
    private final RandomGenerator random;
    private double heat;
    private long lastShot = LONG_AGO;
    private long lastPull = LONG_AGO;
    private long ventedUntil = LONG_AGO;
    private int ventLength = 1;
    private Optional<Window> window = Optional.empty();

    public Heat(Rules rules) {
        this(rules, new Random());
    }

    public Heat(Rules rules, RandomGenerator random) {
        this.rules = rules;
        this.random = random;
    }

    /** Whether a shot leaves the gun at {@code tick}; an overheating shot still does. */
    public Trigger fire(long tick, int minTicksBetween, double heatPerShot) {
        if (venting(tick)) {
            return Trigger.VENTING;
        }
        if (tick - lastShot < minTicksBetween) {
            return Trigger.TOO_SOON;
        }
        lastShot = tick;
        heat = Math.min(FULL, heat + heatPerShot);
        if (heat < FULL) {
            return Trigger.FIRED;
        }
        ventLength = Math.max(1, rules.ventTicks());
        ventedUntil = tick + ventLength;
        int cells = Math.clamp(Math.round(rules.windowShare() * METER_CELLS), 0, METER_CELLS / 2);
        int low = cells == 0 ? 0 : random.nextInt(METER_CELLS / 2 - cells + 1);
        window = cells == 0 ? Optional.empty() : Optional.of(new Window(low, low + cells));
        return Trigger.OVERHEATED;
    }

    /**
     * A pull of the trigger during a vent. Only the first fresh pull counts: a trigger held since the overheating shot
     * does not, and after one try the window is gone.
     */
    public VentCatch pull(long tick) {
        boolean fresh = tick - Math.max(lastPull, lastShot) >= RELEASED_AFTER_TICKS;
        lastPull = tick;
        if (!venting(tick) || !fresh || window.isEmpty()) {
            return VentCatch.NOT_COUNTED;
        }
        var tried = window.get();
        window = Optional.empty();
        int lit = litCells(tick);
        if (lit > tried.low() && lit <= tried.high()) {
            ventedUntil = tick;
            heat = 0;
            return VentCatch.CAUGHT;
        }
        int penalty = (int) Math.round(rules.missPenaltyShare() * rules.ventTicks());
        ventedUntil += penalty;
        ventLength += penalty;
        tick(tick);
        return VentCatch.MISSED;
    }

    /** During a vent the heat follows the vent's progress; otherwise a gun cools once it has paused long enough. */
    public void tick(long tick) {
        if (venting(tick)) {
            heat = FULL * (ventedUntil - tick) / ventLength;
        } else if (tick - lastShot >= rules.coolDelayTicks()) {
            heat = Math.max(0, heat - rules.coolPerTick());
        }
    }

    public boolean venting(long tick) {
        return tick < ventedUntil;
    }

    public int ventTicks(long tick) {
        return (int) Math.max(0, ventedUntil - tick);
    }

    /** The window a pull can still hit during this vent. */
    public Optional<Window> window(long tick) {
        return venting(tick) ? window : Optional.empty();
    }

    /** How many meter cells a vent shows lit: every cell the cooling heat has not yet left. */
    public int litCells(long tick) {
        return (int) Math.ceil((double) ventTicks(tick) * METER_CELLS / ventLength);
    }

    public boolean warning(double threshold, double nextShotHeat) {
        return heat / FULL >= threshold || heat + nextShotHeat >= FULL;
    }

    /** 0 to 1, as the experience bar shows it. */
    public float level() {
        return (float) (heat / FULL);
    }
}
