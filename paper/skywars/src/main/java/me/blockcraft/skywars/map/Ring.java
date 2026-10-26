package me.blockcraft.skywars.map;

import java.util.ArrayList;
import java.util.List;

/** Points evenly round a centre, and which of a ring's slots a smaller round fills. */
public final class Ring {

    public record Point(double x, double z) {
    }

    private Ring() {
    }

    /** {@code count} points at {@code radius} round 0/0, the first at {@code startDegrees} (0 points south, +z). */
    public static List<Point> points(int count, double radius, double startDegrees) {
        var points = new ArrayList<Point>(count);
        for (int index = 0; index < count; index++) {
            double angle = Math.toRadians(startDegrees + 360.0 * index / count);
            points.add(new Point(Math.sin(angle) * radius, Math.cos(angle) * radius));
        }
        return List.copyOf(points);
    }

    /** The slots of a ring of {@code slots} that {@code players} take, spread as evenly as the ring allows. */
    public static List<Integer> spread(int players, int slots) {
        var taken = new ArrayList<Integer>(players);
        for (int index = 0; index < Math.min(players, slots); index++) {
            taken.add((int) Math.floor((double) index * slots / players));
        }
        return List.copyOf(taken);
    }
}
