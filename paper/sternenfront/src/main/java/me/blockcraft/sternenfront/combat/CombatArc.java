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
}
