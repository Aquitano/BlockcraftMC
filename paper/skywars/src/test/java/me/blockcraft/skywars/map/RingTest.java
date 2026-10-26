package me.blockcraft.skywars.map;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RingTest {

    @Test
    public void islandsStandEvenlyOnTheRing() {
        var points = Ring.points(12, 40, 0);

        assertEquals(12, points.size());
        for (var point : points) {
            assertEquals(40, Math.hypot(point.x(), point.z()), 1e-9);
        }
        assertEquals(2 * 40 * Math.sin(Math.PI / 12), Math.hypot(points.get(0).x() - points.get(1).x(),
                points.get(0).z() - points.get(1).z()), 1e-9);
    }

    @Test
    public void aSmallRoundTakesSlotsSpreadRoundTheRing() {
        assertEquals(List.of(0, 3, 6, 9), Ring.spread(4, 12));
        assertEquals(List.of(0, 2, 4, 7, 9), Ring.spread(5, 12));
        assertEquals(12, new HashSet<>(Ring.spread(12, 12)).size());
    }
}
