package me.blockcraft.sternenfront.combat;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class CombatArc {

    private CombatArc() { }

    public static Vector nearest(Vector eye, BoundingBox target) {
        return new Vector(Math.clamp(eye.getX(), target.getMinX(), target.getMaxX()),
                Math.clamp(eye.getY(), target.getMinY(), target.getMaxY()),
                Math.clamp(eye.getZ(), target.getMinZ(), target.getMaxZ()));
    }

    public static boolean hits(Vector eye, Vector facing, BoundingBox target, double range, double angle) {
        var toward = nearest(eye, target).subtract(eye);
        return toward.lengthSquared() <= range * range && (toward.lengthSquared() < 0.001
                || facing.dot(toward.normalize()) >= Math.cos(Math.toRadians(angle / 2)));
    }
}
