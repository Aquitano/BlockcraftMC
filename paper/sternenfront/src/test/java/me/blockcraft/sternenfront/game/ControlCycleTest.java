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
        assertEquals(ControlCycle.Phase.ACTIVE, cycle.phase(619));
        assertEquals(ControlCycle.Phase.READY, cycle.phase(620));
    }
}
