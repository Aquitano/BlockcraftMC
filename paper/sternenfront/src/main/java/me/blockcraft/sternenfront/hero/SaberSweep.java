package me.blockcraft.sternenfront.hero;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class SaberSweep {

    private SaberSweep() { }

    static boolean hits(Vector from, Vector to, BoundingBox target, double radius) {
        var bounds = target.clone().expand(radius);
        if (bounds.contains(from)) {
            return true;
        }
        var direction = to.clone().subtract(from);
        double distance = direction.length();
        return distance > 0 && bounds.rayTrace(from, direction.multiply(1 / distance), distance) != null;
    }
}
