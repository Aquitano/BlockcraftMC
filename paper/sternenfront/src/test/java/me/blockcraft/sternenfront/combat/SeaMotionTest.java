package me.blockcraft.sternenfront.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SeaMotionTest {

    @Test
    public void crestsBreakAtTheSupportAndRestartOffshore() {
        var wave = new SeaMotion(120, 20, 12);
        assertEquals(12, wave.distance(0, 0), 0.001);
        assertEquals(6, wave.distance(50, 0), 0.001);
        assertEquals(0, wave.distance(100, 0), 0.001);
        assertEquals(0, wave.distance(110, 0), 0.001);
        assertEquals(0, wave.impact(50, 0), 0.001);
        assertEquals(1, wave.impact(110, 0), 0.001);
        assertEquals(12, wave.distance(120, 0), 0.001);
        assertEquals(0, wave.impact(120, 0), 0.001);
        assertEquals(wave.distance(50, 0), wave.distance(35, 15), 0.001);
    }
}
