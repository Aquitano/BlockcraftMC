package me.blockcraft.sternenfront.game;

/**
 * A blaster's heat instead of ammunition: every shot heats it, a pause cools it, and a gun that reaches full heat
 * vents for {@link Rules#ventTicks} before it fires again, cooling from full to nothing meanwhile.
 */
public final class Heat {

    public enum Shot { FIRED, OVERHEATED, TOO_SOON, VENTING }

    public record Rules(int coolDelayTicks, double coolPerTick, int ventTicks) {
    }

    private static final double FULL = 100;
    private static final long LONG_AGO = Long.MIN_VALUE / 2;

    private final Rules rules;
    private double heat;
    private long lastShot = LONG_AGO;
    private long ventedUntil = LONG_AGO;

    public Heat(Rules rules) {
        this.rules = rules;
    }

    /** Whether a shot leaves the gun at {@code tick}; an overheating shot still does. */
    public Shot fire(long tick, int minTicksBetween, double heatPerShot) {
        if (venting(tick)) {
            return Shot.VENTING;
        }
        if (tick - lastShot < minTicksBetween) {
            return Shot.TOO_SOON;
        }
        lastShot = tick;
        heat = Math.min(FULL, heat + heatPerShot);
        if (heat < FULL) {
            return Shot.FIRED;
        }
        ventedUntil = tick + Math.max(1, rules.ventTicks());
        return Shot.OVERHEATED;
    }

    public void cool(long tick) {
        if (venting(tick)) {
            heat = FULL * (ventedUntil - tick) / Math.max(1, rules.ventTicks());
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

    public boolean warning(double threshold, double nextShotHeat) {
        return heat / FULL >= threshold || heat + nextShotHeat >= FULL;
    }

    /** 0 to 1, as the experience bar shows it. */
    public float level() {
        return (float) (heat / FULL);
    }
}
