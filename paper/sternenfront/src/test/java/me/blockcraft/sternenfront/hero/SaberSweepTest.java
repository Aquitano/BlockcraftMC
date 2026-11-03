package me.blockcraft.sternenfront.hero;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SaberSweepTest {

    @Test
    public void hitsAnEnemyCrossedBetweenTicksWithoutExtendingTheFlight() {
        var target = new BoundingBox(2, 0, -0.3, 2.6, 1.8, 0.3);
        var start = new Vector(0, 1, 0);

        assertTrue(SaberSweep.hits(start, new Vector(5, 1, 0), target, 0.6));
        assertFalse(SaberSweep.hits(start, new Vector(1, 1, 0), target, 0.6));
        assertFalse(SaberSweep.hits(start, new Vector(-5, 1, 0), target, 0.6));
        assertFalse(SaberSweep.hits(new Vector(0, 1, 2), new Vector(5, 1, 2), target, 0.6));
    }
}
