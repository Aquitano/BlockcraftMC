package me.blockcraft.sternenfront.game;

import java.util.Optional;

/**
 * Who holds a command post and how far a capture has come: progress runs from -100, held by the Hegemonie, to 100,
 * held by the Kolonien. The faction with more players in the zone moves it, faster the bigger its lead; an enemy's
 * post must be neutralised at 0 before it can be taken; equal numbers hold it still, and an empty zone drifts back
 * towards the post's current state.
 */
public final class CommandPost {

    public sealed interface Change {
        record Neutralised(Faction from) implements Change { }

        record Captured(Faction by) implements Change { }
    }

    public static final double FULL = 100;
    private static final int MAX_LEAD = 3;
    private static final double DRIFT = 0.5;
    private static final double SNAP = 1e-6;

    private final double ratePerSecond;
    private double progress;
    private Optional<Faction> owner = Optional.empty();

    public CommandPost(int captureSeconds) {
        this.ratePerSecond = FULL / Math.max(1, captureSeconds);
    }

    public Optional<Faction> owner() {
        return owner;
    }

    /** How far the post leans towards {@code faction}, 0 to 1. */
    public double share(Faction faction) {
        return Math.max(0, progress * sign(faction)) / FULL;
    }

    /** The faction the progress leans towards, if it leans at all. */
    public Optional<Faction> leaning() {
        return progress == 0 ? Optional.empty() : Optional.of(progress > 0 ? Faction.KOLONIEN : Faction.HEGEMONIE);
    }

    public static boolean contested(int kolonien, int hegemonie) {
        return kolonien > 0 && kolonien == hegemonie;
    }

    /** One step of {@code seconds} with these numbers of players in the zone. */
    public Optional<Change> update(int kolonien, int hegemonie, double seconds) {
        int lead = kolonien - hegemonie;
        if (lead == 0) {
            if (kolonien == 0) {
                drift(seconds);
            }
            return Optional.empty();
        }
        var leader = lead > 0 ? Faction.KOLONIEN : Faction.HEGEMONIE;
        double step = ratePerSecond * Math.min(Math.abs(lead), MAX_LEAD) * seconds;
        if (owner.isPresent() && owner.get() != leader) {
            var from = owner.get();
            progress = moveTowards(progress, 0, step);
            if (progress == 0) {
                owner = Optional.empty();
                return Optional.of(new Change.Neutralised(from));
            }
            return Optional.empty();
        }
        progress = moveTowards(progress, FULL * sign(leader), step);
        if (owner.isEmpty() && progress == FULL * sign(leader)) {
            owner = Optional.of(leader);
            return Optional.of(new Change.Captured(leader));
        }
        return Optional.empty();
    }

    private void drift(double seconds) {
        double target = owner.map(faction -> FULL * sign(faction)).orElse(0.0);
        progress = moveTowards(progress, target, ratePerSecond * DRIFT * seconds);
    }

    private static int sign(Faction faction) {
        return faction == Faction.KOLONIEN ? 1 : -1;
    }

    /** Lands exactly on {@code to} when the remaining gap is only rounding, so thirds of a capture add up to one. */
    private static double moveTowards(double from, double to, double step) {
        double next = from < to ? from + step : from - step;
        return Math.abs(to - next) < SNAP || (from < to) == (next > to) ? to : next;
    }
}
