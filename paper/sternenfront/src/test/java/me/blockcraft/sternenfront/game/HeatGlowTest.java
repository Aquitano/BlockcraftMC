package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HeatGlowTest {

    private static final HeatGlow.Steps STEPS = new HeatGlow.Steps(0.5, 0.8, 4);

    @Test
    public void theVentsWarmInStepsAndPulseWhileVenting() {
        assertEquals(HeatGlow.COLD, HeatGlow.of(STEPS, 0.49, false, 0));
        assertEquals(HeatGlow.WARM, HeatGlow.of(STEPS, 0.5, false, 0));
        assertEquals(HeatGlow.HOT, HeatGlow.of(STEPS, 0.8, false, 0));
        assertEquals(HeatGlow.VENTING, HeatGlow.of(STEPS, 0.3, true, 3));
        assertEquals(HeatGlow.VENTING_DIM, HeatGlow.of(STEPS, 0.3, true, 4));
    }
}
