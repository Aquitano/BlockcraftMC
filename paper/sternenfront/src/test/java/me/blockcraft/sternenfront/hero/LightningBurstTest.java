package me.blockcraft.sternenfront.hero;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LightningBurstTest {

    @Test
    public void dealsTheConfiguredTotalDamageWithoutRepeatingAPulseInTheSameTick() {
        var burst = new LightningBurst(new HeroConfig.Lightning(10, 50, 30, 5, 3, 12), 100);
        double damage = 0;
        for (long tick = 100; tick < 130; tick++) {
            if (burst.pulse(tick)) {
                damage += burst.damage();
            }
            assertFalse(burst.pulse(tick));
        }
        assertEquals(12, damage, 0.001);
        assertFalse(burst.active(130));
        assertFalse(burst.pulse(130));
    }

    @Test
    public void includesTheLastPulseOfAnUnevenDurationAndExpiresWithoutCatchingUp() {
        var rules = new HeroConfig.Lightning(10, 50, 11, 5, 3, 12);
        var burst = new LightningBurst(rules, 0);
        assertTrue(burst.pulse(0));
        assertFalse(burst.pulse(4));
        assertTrue(burst.pulse(5));
        assertTrue(burst.pulse(10));
        assertEquals(4, burst.damage(), 0.001);
        assertFalse(burst.pulse(100));
    }
}
