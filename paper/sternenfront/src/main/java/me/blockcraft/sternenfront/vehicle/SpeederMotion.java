package me.blockcraft.sternenfront.vehicle;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

public final class SpeederMotion {

    public static final double STEP_HEIGHT = 1;
    public static final long NEVER = Long.MIN_VALUE / 2;

    private static final double SETTLED = 0.01;
    private static final double MAX_HEIGHT_LAG = 1.5;
    private static final double KNOCK_FORWARD_SHARE = 0.35;
    private static final double KNOCK_UP_SHARE = 0.4;

    public record Handling(double reverse, double acceleration, double braking) { }

    public record Steering(double degrees, double standstillShare, double fullTurnSpeed) { }

    public record BoostRules(double speed, double drainPerTick, double rechargePerTick, double restartShare) { }

    /** Once empty, a boost stays off until it has recharged to {@code restartShare}, so holding sprint cannot stutter. */
    public record Boost(double charge, boolean active) {

        public static final Boost FULL = new Boost(1, false);

        public Boost next(boolean wanted, BoostRules rules) {
            boolean burning = wanted && (active ? charge > 0 : charge >= rules.restartShare());
            double left = burning ? charge - rules.drainPerTick() : charge + rules.rechargePerTick();
            return new Boost(Math.clamp(left, 0, 1), burning);
        }
    }

    private SpeederMotion() { }

    public static double speed(double current, int throttle, double top, Handling handling) {
        if (current * throttle < 0) {
            return Math.copySign(Math.max(0, Math.abs(current) - handling.braking()), current);
        }
        double wanted = throttle > 0 ? top : throttle < 0 ? -handling.reverse() : 0;
        return current + Math.clamp(wanted - current, -handling.acceleration(), handling.acceleration());
    }

    /** Degrees of yaw this tick, positive to the right: little at standstill, and mirrored in reverse like a car. */
    public static double turn(int steer, double speed, Steering steering) {
        double moving = Math.min(1, Math.abs(speed) / steering.fullTurnSpeed());
        double share = steering.standstillShare() + (1 - steering.standstillShare()) * moving;
        return steer * steering.degrees() * share * (speed < 0 ? -1 : 1);
    }

    public static boolean rams(double speed, double minimumSpeed, long tick, long lastHit, int cooldownTicks) {
        return Math.abs(speed) >= minimumSpeed && tick - lastHit >= cooldownTicks;
    }

    public static double ramDamage(double speed, double topSpeed, double damageAtTopSpeed) {
        return damageAtTopSpeed * Math.abs(speed) / topSpeed;
    }

    /** Sideways off the craft's line, to whichever side the target already stands, with a little of its momentum. */
    public static Vector knockAside(Vector forward, Vector toTarget, double strength) {
        var side = new Vector(-forward.getZ(), 0, forward.getX());
        double away = side.dot(toTarget) < 0 ? -1 : 1;
        return side.multiply(away * strength).add(forward.clone().multiply(strength * KNOCK_FORWARD_SHARE))
                .setY(strength * KNOCK_UP_SHARE);
    }

    public static double smoothHeight(double shown, double actual, double share) {
        double next = shown + (actual - shown) * share;
        return Math.abs(actual - next) < SETTLED ? actual : Math.clamp(next, actual - MAX_HEIGHT_LAG, actual + MAX_HEIGHT_LAG);
    }

    public static boolean blocked(BoundingBox occupied, Vector movement, boolean grounded, Predicate<BoundingBox> collides) {
        var path = occupied.clone().expandDirectional(movement);
        return collides.test(path) && (!grounded
                || collides.test(occupied.clone().expandDirectional(0, STEP_HEIGHT, 0))
                || collides.test(path.shift(0, STEP_HEIGHT, 0)));
    }
}
