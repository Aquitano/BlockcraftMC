package me.blockcraft.endergames.world;

import java.util.List;
import java.util.stream.IntStream;

/** Places spread evenly on a circle, where the players start as far from each other as the map allows. */
public final class Ring {

    public record Point(double x, double z) {
    }

    private Ring() {
    }

    /** {@code count} points on the circle of {@code radius} around the centre, the first at {@code phase} radians. */
    public static List<Point> points(double centerX, double centerZ, double radius, int count, double phase) {
        return IntStream.range(0, count)
                .mapToObj(index -> phase + 2 * Math.PI * index / count)
                .map(angle -> new Point(centerX + radius * Math.cos(angle), centerZ + radius * Math.sin(angle)))
                .toList();
    }
}
