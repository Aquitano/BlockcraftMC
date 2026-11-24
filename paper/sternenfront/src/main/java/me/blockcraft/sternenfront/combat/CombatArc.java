package me.blockcraft.sternenfront.combat;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class CombatArc {

    private static final double SAMPLE_SPACING = 0.12;

    private CombatArc() { }

    public static List<Vector> rays(Vector facing, Vector side, double range, double angle, boolean reverse) {
        double radians = Math.toRadians(angle);
        int steps = (int) Math.ceil(range * radians / SAMPLE_SPACING);
        var rays = new ArrayList<Vector>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double turn = (step / (double) steps - 0.5) * radians * (reverse ? -1 : 1);
            rays.add(facing.clone().multiply(Math.cos(turn)).add(side.clone().multiply(Math.sin(turn))).normalize());
        }
        return List.copyOf(rays);
    }

    public static boolean hits(Vector origin, Vector direction, BoundingBox target, double reach) {
        return reach > 0 && (target.contains(origin) || target.rayTrace(origin, direction, reach) != null);
    }

    /** Whether a blade {@code radius} wide, carried in a straight line from {@code from} to {@code to}, meets the target. */
    public static boolean sweeps(Vector from, Vector to, BoundingBox target, double radius) {
        var bounds = target.clone().expand(radius);
        var direction = to.clone().subtract(from);
        double distance = direction.length();
        return bounds.contains(from) || distance > 0 && bounds.rayTrace(from, direction.multiply(1 / distance), distance) != null;
    }
}
