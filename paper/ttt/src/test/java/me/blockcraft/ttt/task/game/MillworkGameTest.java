package me.blockcraft.ttt.task.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MillworkGameTest {

    @Test
    public void markerBouncesBetweenTheEnds() {
        int[] positions = new int[18];
        for (int tick = 0; tick < positions.length; tick++) {
            positions[tick] = MillworkGame.markerPosition(tick, 1, 9);
        }
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1, 0, 1]", java.util.Arrays.toString(positions));
    }

    @Test
    public void markerStaysOnAStepForItsTicks() {
        assertEquals(1, MillworkGame.markerPosition(4, 4, 9));
        assertEquals(1, MillworkGame.markerPosition(7, 4, 9));
        assertEquals(2, MillworkGame.markerPosition(8, 4, 9));
    }
}
