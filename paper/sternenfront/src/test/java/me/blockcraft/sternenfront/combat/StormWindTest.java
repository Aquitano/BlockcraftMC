package me.blockcraft.sternenfront.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StormWindTest {

    private final StormWind wind = new StormWind(100, 140, 40, 1.5);

    @Test
    public void gustsSwellAndFallOnAFixedPeriod() {
        assertTrue(wind.gustStarts(100));
        assertTrue(wind.gustStarts(240));
        assertFalse(wind.gustStarts(241));
        assertEquals(1, wind.gust(120), 0.001);
        assertEquals(0, wind.gust(140), 0.001);
        assertEquals(1, wind.gust(260), 0.001);
    }

    @Test
    public void particlesFollowTheWindsStrength() {
        assertEquals(10, wind.particles(200, 10));
        assertEquals(25, wind.particles(120, 10));
    }
}
