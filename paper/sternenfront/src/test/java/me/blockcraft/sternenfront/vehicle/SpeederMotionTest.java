package me.blockcraft.sternenfront.vehicle;

import org.junit.Test;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeederMotionTest {

    @Test
    public void stepsOverOneBlockWithRiderClearanceButStopsAtWallsAndLowCeilings() {
        var occupied = new BoundingBox(-0.45, 0, -0.45, 0.45, 2.5, 0.45);
        var forward = new Vector(0, 0, 0.65);
        var step = new BoundingBox(-1, 0, 1, 1, 1, 2);
        var wall = new BoundingBox(-1, 0, 1, 1, 2, 2);
        var ceiling = new BoundingBox(-1, 3, 0, 1, 4, 2);
        assertFalse(SpeederMotion.blocked(occupied, forward, true, step::overlaps));
        assertTrue(SpeederMotion.blocked(occupied, forward, true, wall::overlaps));
        assertTrue(SpeederMotion.blocked(occupied, forward, true,
                box -> List.of(step, ceiling).stream().anyMatch(box::overlaps)));
        assertTrue(SpeederMotion.blocked(occupied, forward, false, step::overlaps));
        assertFalse(SpeederMotion.blocked(occupied, new Vector(0, 0, -0.65), true,
                step.clone().shift(0, 0, -3)::overlaps));
        assertEquals(0, occupied.getMinY(), 0);
    }

    @Test
    public void acceleratesWithinLimitsAndBrakesBeforeReversing() {
        double speed = 0;
        for (int tick = 0; tick < 100; tick++) {
            speed = SpeederMotion.speed(speed, 1, 0.65, 0.035);
        }
        assertEquals(0.65, speed, 0.001);
        assertTrue(SpeederMotion.speed(speed, -1, 0.65, 0.035) > 0);
        for (int tick = 0; tick < 100; tick++) {
            speed = SpeederMotion.speed(speed, -1, 0.65, 0.035);
        }
        assertEquals(-0.65 * 0.35, speed, 0.001);
        for (int tick = 0; tick < 100; tick++) {
            speed = SpeederMotion.speed(speed, 0, 0.65, 0.035);
        }
        assertEquals(0, speed, 0.001);
    }
}
