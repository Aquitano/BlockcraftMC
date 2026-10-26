package me.blockcraft.endergames.world;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RingTest {

    @Test
    public void playersStartEvenlyOnTheRing() {
        var points = Ring.points(100, -50, 120, 4, 0);

        assertEquals(4, points.size());
        for (var point : points) {
            assertEquals(120, Math.hypot(point.x() - 100, point.z() + 50), 1e-9);
        }
        var first = points.get(0);
        var second = points.get(1);
        assertEquals(120 * Math.sqrt(2), Math.hypot(first.x() - second.x(), first.z() - second.z()), 1e-9);
    }
}
