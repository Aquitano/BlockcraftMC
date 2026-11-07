package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LightningPathTest {

    @Test
    public void aBoltKeepsItsEndsAndStaysNearTheLineBetweenThem() {
        var from = new Vector(0, 1, 0);
        var to = new Vector(8, 1, 0);
        var bolt = LightningPath.arc(from, to, 3, 0.1, new Random(4));
        assertEquals(9, bolt.size());
        assertEquals(from, bolt.getFirst());
        assertEquals(to, bolt.getLast());
        // Each level pushes a midpoint at most a tenth of its segment, 8, 4 and 2 blocks long.
        double reach = 0.1 * (8 + 4 + 2) / 2 + 1e-9;
        assertTrue(bolt.stream().allMatch(point -> Math.hypot(point.getY() - 1, point.getZ()) <= reach));
        assertTrue(bolt.stream().anyMatch(point -> Math.hypot(point.getY() - 1, point.getZ()) > 0.01));
    }

    @Test
    public void theSameSeedDrawsTheSameBolt() {
        var from = new Vector(0, 0, 0);
        var to = new Vector(0, 0, 6);
        assertEquals(LightningPath.arc(from, to, 3, 0.1, new Random(9)), LightningPath.arc(from, to, 3, 0.1, new Random(9)));
    }

    @Test
    public void forksSplitOffTheBoltAtItsInnerPointsWithinTheirLength() {
        var bolt = LightningPath.arc(new Vector(0, 0, 0), new Vector(6, 0, 0), 3, 0.1, new Random(2));
        var forks = LightningPath.forks(bolt, 2, 0.5, 1, 0.1, new Random(3));
        assertEquals(2, forks.size());
        for (var fork : forks) {
            assertTrue(bolt.subList(1, bolt.size() - 1).contains(fork.getFirst()));
            double length = fork.getLast().distance(fork.getFirst());
            assertTrue(length >= 0.5 - 1e-9 && length <= 1 + 1e-9);
        }
    }
}
