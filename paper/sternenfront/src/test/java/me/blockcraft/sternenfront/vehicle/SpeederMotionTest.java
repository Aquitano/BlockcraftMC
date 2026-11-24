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
    public void acceleratesWithinLimitsAndBrakesHardBeforeASlowReverse() {
        var handling = new SpeederMotion.Handling(0.2, 0.035, 0.07);
        double speed = 0;
        for (int tick = 0; tick < 100; tick++) {
            speed = SpeederMotion.speed(speed, 1, 0.65, handling);
        }
        assertEquals(0.65, speed, 0.001);
        assertEquals(0.58, SpeederMotion.speed(speed, -1, 0.65, handling), 0.001);
        assertEquals(0, SpeederMotion.speed(0.05, -1, 0.65, handling), 0);
        assertEquals(0, SpeederMotion.speed(-0.05, 1, 0.65, handling), 0);
        for (int tick = 0; tick < 100; tick++) {
            speed = SpeederMotion.speed(speed, -1, 0.65, handling);
        }
        assertEquals(-0.2, speed, 0.001);
        for (int tick = 0; tick < 100; tick++) {
            speed = SpeederMotion.speed(speed, 0, 0.65, handling);
        }
        assertEquals(0, speed, 0.001);
    }

    @Test
    public void steeringScalesWithSpeedAndMirrorsInReverse() {
        var steering = new SpeederMotion.Steering(4, 0.15, 0.35);
        assertEquals(0.6, SpeederMotion.turn(1, 0, steering), 0.001);
        assertEquals(4, SpeederMotion.turn(1, 0.65, steering), 0.001);
        assertEquals(-4, SpeederMotion.turn(-1, 0.65, steering), 0.001);
        assertTrue(SpeederMotion.turn(1, -0.2, steering) < 0);
        assertEquals(SpeederMotion.turn(1, 0.2, steering), -SpeederMotion.turn(1, -0.2, steering), 0.001);
    }

    @Test
    public void boostBurnsItsBudgetThenWaitsForTheRestartShare() {
        var rules = new SpeederMotion.BoostRules(0.95, 0.25, 0.1, 0.5);
        var boost = SpeederMotion.Boost.FULL;
        for (int tick = 0; tick < 4; tick++) {
            boost = boost.next(true, rules);
            assertTrue(boost.active());
        }
        assertEquals(0, boost.charge(), 0.001);
        for (int tick = 0; tick < 4; tick++) {
            boost = boost.next(true, rules);
            assertFalse(boost.active());
        }
        assertEquals(0.4, boost.charge(), 0.001);
        boost = boost.next(true, rules);
        assertFalse(boost.active());
        assertTrue(boost.next(true, rules).active());
        assertEquals(1, new SpeederMotion.Boost(0.95, false).next(false, rules).charge(), 0);
    }

    @Test
    public void ramsAboveTheMinimumSpeedAtMostOncePerCooldownWithDamageScaledBySpeed() {
        assertTrue(SpeederMotion.rams(0.5, 0.4, 100, SpeederMotion.NEVER, 20));
        assertFalse(SpeederMotion.rams(0.3, 0.4, 100, SpeederMotion.NEVER, 20));
        assertFalse(SpeederMotion.rams(0.5, 0.4, 100, 81, 20));
        assertTrue(SpeederMotion.rams(0.5, 0.4, 100, 80, 20));
        assertEquals(12, SpeederMotion.ramDamage(0.65, 0.65, 12), 0.001);
        assertEquals(6, SpeederMotion.ramDamage(0.325, 0.65, 12), 0.001);
    }
}
