package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The jagged line of a Force lightning bolt, and the short forks that split off it. */
public final class LightningPath {

    private LightningPath() { }

    /**
     * Halves every segment {@code levels} times and pushes each new midpoint sideways by up to {@code roughness} of
     * its segment's length, so the bolt keeps its ends and zigzags between them.
     */
    public static List<Vector> arc(Vector from, Vector to, int levels, double roughness, Random random) {
        var points = new ArrayList<>(List.of(from.clone(), to.clone()));
        for (int level = 0; level < levels; level++) {
            var split = new ArrayList<Vector>(points.size() * 2 - 1);
            for (int index = 0; index + 1 < points.size(); index++) {
                var start = points.get(index);
                var end = points.get(index + 1);
                var along = end.clone().subtract(start);
                split.add(start);
                split.add(start.clone().midpoint(end).add(sideways(along, random).multiply(random.nextDouble(-1, 1) * roughness * along.length())));
            }
            split.add(points.getLast());
            points = split;
        }
        return points;
    }

    /** Up to {@code count} short arcs, each {@code minLength} to {@code maxLength} long, from inner points of {@code bolt}. */
    public static List<List<Vector>> forks(List<Vector> bolt, int count, double minLength, double maxLength, double roughness, Random random) {
        var forks = new ArrayList<List<Vector>>(count);
        for (int fork = 0; fork < count && bolt.size() > 2; fork++) {
            int at = 1 + random.nextInt(bolt.size() - 2);
            var root = bolt.get(at);
            var along = bolt.get(at + 1).clone().subtract(root);
            if (along.lengthSquared() < 1e-9) {
                continue;
            }
            var direction = along.normalize().add(sideways(along, random).multiply(0.8)).normalize();
            var tip = root.clone().add(direction.multiply(random.nextDouble(minLength, maxLength)));
            forks.add(arc(root, tip, 2, roughness, random));
        }
        return forks;
    }

    /** A random unit vector square to {@code along}. */
    private static Vector sideways(Vector along, Random random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            var guess = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            var square = guess.subtract(along.clone().multiply(guess.dot(along) / Math.max(along.lengthSquared(), 1e-9)));
            if (square.lengthSquared() > 1e-6) {
                return square.normalize();
            }
        }
        return new Vector();
    }
}
