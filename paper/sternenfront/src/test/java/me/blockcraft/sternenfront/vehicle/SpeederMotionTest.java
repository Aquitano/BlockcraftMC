package me.blockcraft.sternenfront.vehicle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpeederMotionTest {

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
