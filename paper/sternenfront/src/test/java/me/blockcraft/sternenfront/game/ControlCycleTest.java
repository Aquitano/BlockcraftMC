package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlCycleTest {

    @Test
    public void warnsBeforeActivationExpiresAndCannotRetriggerDuringItsCooldown() {
        var cycle = new ControlCycle();
        assertTrue(cycle.trigger(100, 20, 120, 400));
        assertEquals(ControlCycle.Phase.WARNING, cycle.phase(119));
        assertEquals(ControlCycle.Phase.ACTIVE, cycle.phase(120));
        assertEquals(ControlCycle.Phase.COOLDOWN, cycle.phase(240));
        assertFalse(cycle.trigger(240, 0, 120, 400));
        assertTrue(cycle.trigger(500, 0, 120, 10));
        assertEquals(120, cycle.ticksUntilReady(500));
        assertEquals(ControlCycle.Phase.ACTIVE, cycle.phase(619));
        assertEquals(ControlCycle.Phase.READY, cycle.phase(620));
    }

    @Test
    public void readinessCountdownIncludesWarningActivationAndCooldown() {
        var cycle = new ControlCycle();
        assertEquals(0, cycle.ticksUntilReady(100));
        assertTrue(cycle.trigger(100, 20, 120, 400));
        assertEquals(400, cycle.ticksUntilReady(100));
        assertEquals(20, cycle.remaining(100));
        assertEquals(380, cycle.ticksUntilReady(120));
        assertEquals(120, cycle.remaining(120));
        assertEquals(260, cycle.ticksUntilReady(240));
        assertEquals(260, cycle.remaining(240));
        assertEquals(1, cycle.ticksUntilReady(499));
        assertEquals(0, cycle.ticksUntilReady(500));
        assertEquals(0, cycle.ticksUntilReady(501));
    }
}
