package me.blockcraft.sternenfront.vehicle;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

public final class SpeederMotion {

    public static final double STEP_HEIGHT = 1;

    private SpeederMotion() { }

    public static double speed(double current, int throttle, double maximum, double acceleration) {
        double wanted = throttle > 0 ? maximum : throttle < 0 ? -maximum * 0.35 : 0;
        return current + Math.clamp(wanted - current, -acceleration, acceleration);
    }

    public static boolean blocked(BoundingBox occupied, Vector movement, boolean grounded, Predicate<BoundingBox> collides) {
        var path = occupied.clone().expandDirectional(movement);
        return collides.test(path) && (!grounded
                || collides.test(occupied.clone().expandDirectional(0, STEP_HEIGHT, 0))
                || collides.test(path.shift(0, STEP_HEIGHT, 0)));
    }
}
