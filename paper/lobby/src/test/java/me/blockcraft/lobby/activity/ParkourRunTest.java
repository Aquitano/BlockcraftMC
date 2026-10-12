package me.blockcraft.lobby.activity;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParkourRunTest {

    @Test
    public void checkpointsMustBeReachedInOrderAndFallsKeepTheClockRunning() {
        var run = new ParkourRun(1_000_000_000L);
        assertFalse(run.reach(2));
        assertTrue(run.reach(1));
        assertFalse(run.reach(1));
        assertEquals(1, run.fall());
        assertEquals(1, run.checkpoint());
        assertTrue(run.reach(2));
        assertEquals("01:12.4", ParkourRun.time(run.millis(73_400_000_000L)));
    }
}
