package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeatTest {

    private static final Heat.Rules RULES = new Heat.Rules(10, 5, 40);

    @Test
    public void aGunFiresNoFasterThanItsShotIntervalAndHeatsWithEachShot() {
        var heat = new Heat(RULES);

        assertEquals(Heat.Shot.FIRED, heat.fire(0, 4, 20));
        assertEquals(Heat.Shot.TOO_SOON, heat.fire(3, 4, 20));
        assertEquals(Heat.Shot.FIRED, heat.fire(4, 4, 20));

        assertEquals(0.4f, heat.level(), 1e-6);
    }

    @Test
    public void fullHeatVentsBeforeTheNextShotAndCoolsToNothingMeanwhile() {
        var heat = new Heat(RULES);
        heat.fire(0, 1, 60);

        assertEquals(Heat.Shot.OVERHEATED, heat.fire(1, 1, 60));
        assertEquals(Heat.Shot.VENTING, heat.fire(20, 1, 60));
        heat.cool(21);
        assertEquals(0.5f, heat.level(), 1e-6);

        heat.cool(41);
        assertFalse(heat.venting(41));
        assertEquals(Heat.Shot.FIRED, heat.fire(41, 1, 10));
    }

    @Test
    public void aPauseCoolsTheGunOnlyAfterTheDelay() {
        var heat = new Heat(RULES);
        heat.fire(0, 1, 50);

        heat.cool(9);
        assertEquals(0.5f, heat.level(), 1e-6);
        heat.cool(10);
        assertEquals(0.45f, heat.level(), 1e-6);
    }

    @Test
    public void warningCatchesARifleThatWouldOverheatBeforeReachingTheThreshold() {
        var heat = new Heat(RULES);
        heat.fire(0, 1, 34);
        assertFalse(heat.warning(0.8, 34));
        heat.fire(1, 1, 34);
        assertTrue(heat.warning(0.8, 34));
        assertFalse(heat.warning(0.8, 8));
        heat.fire(2, 1, 34);
        assertEquals(40, heat.ventTicks(2));
        assertEquals(20, heat.ventTicks(22));
        assertEquals(0, heat.ventTicks(42));
    }
}
