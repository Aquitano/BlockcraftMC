package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LagCompensationTest {

    private static final LagCompensation.Settings SETTINGS = new LagCompensation.Settings(true, 200, 100);

    @Test
    public void theRewindIsThePingAndTheClientsSmoothingInTicks() {
        assertEquals(2, SETTINGS.rewindTicks(0), 1e-9);
        assertEquals(3.6, SETTINGS.rewindTicks(80), 1e-9);
    }

    @Test
    public void theRewindStopsAtTheCap() {
        assertEquals(4, SETTINGS.rewindTicks(100), 1e-9);
        assertEquals(4, SETTINGS.rewindTicks(900), 1e-9);
    }

    @Test
    public void turnedOffEveryShotSeesThePresent() {
        assertEquals(0, new LagCompensation.Settings(false, 200, 100).rewindTicks(80), 1e-9);
    }

    @Test
    public void theHistoryReachesPastTheCap() {
        assertEquals(6, SETTINGS.ticksKept());
    }
}
