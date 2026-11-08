package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeatTest {

    private static final Heat.Rules RULES = new Heat.Rules(10, 5, 40, 0.2, 0.25);
    private static final RandomGenerator LOWEST_WINDOW = () -> 0L;

    private static Heat overheatedAt(long tick) {
        var heat = new Heat(RULES, LOWEST_WINDOW);
        heat.fire(tick - 1, 1, 60);
        assertEquals(Heat.Trigger.OVERHEATED, heat.fire(tick, 1, 60));
        return heat;
    }

    @Test
    public void aGunFiresNoFasterThanItsShotIntervalAndHeatsWithEachShot() {
        var heat = new Heat(RULES);

        assertEquals(Heat.Trigger.FIRED, heat.fire(0, 4, 20));
        assertEquals(Heat.Trigger.TOO_SOON, heat.fire(3, 4, 20));
        assertEquals(Heat.Trigger.FIRED, heat.fire(4, 4, 20));

        assertEquals(0.4f, heat.level(), 1e-6);
    }

    @Test
    public void fullHeatVentsBeforeTheNextShotAndCoolsToNothingMeanwhile() {
        var heat = new Heat(RULES);
        heat.fire(0, 1, 60);

        assertEquals(Heat.Trigger.OVERHEATED, heat.fire(1, 1, 60));
        assertEquals(Heat.Trigger.VENTING, heat.fire(20, 1, 60));
        heat.tick(21);
        assertEquals(0.5f, heat.level(), 1e-6);

        heat.tick(41);
        assertFalse(heat.venting(41));
        assertEquals(Heat.Trigger.FIRED, heat.fire(41, 1, 10));
    }

    @Test
    public void aPauseCoolsTheGunOnlyAfterTheDelay() {
        var heat = new Heat(RULES);
        heat.fire(0, 1, 50);

        heat.tick(9);
        assertEquals(0.5f, heat.level(), 1e-6);
        heat.tick(10);
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

    @Test
    public void aFreshPullWhileTheMetersEdgeIsInTheWindowEndsTheVent() {
        var heat = overheatedAt(10);
        assertEquals(Optional.of(new Heat.Window(0, 2)), heat.window(10));

        assertEquals(2, heat.litCells(45));
        assertEquals(Heat.VentCatch.CAUGHT, heat.pull(45));
        assertFalse(heat.venting(45));
        assertEquals(0f, heat.level(), 1e-6);
        assertEquals(Heat.Trigger.FIRED, heat.fire(45, 1, 10));
    }

    @Test
    public void aMissLengthensTheVentOnceAndUsesUpTheTry() {
        var heat = overheatedAt(10);

        assertEquals(Heat.VentCatch.MISSED, heat.pull(20));
        assertEquals(40, heat.ventTicks(20));
        assertEquals(Optional.empty(), heat.window(20));
        assertEquals(Heat.VentCatch.NOT_COUNTED, heat.pull(52));
        assertTrue(heat.venting(52));
    }

    @Test
    public void aTriggerHeldThroughTheOverheatNeitherHitsNorMisses() {
        var heat = overheatedAt(10);
        for (long tick : new long[] {14, 18, 25, 29, 36, 40, 46}) {
            assertEquals(Heat.VentCatch.NOT_COUNTED, heat.pull(tick));
        }
        assertTrue(heat.venting(46));
        assertEquals(Optional.of(new Heat.Window(0, 2)), heat.window(46));
    }
}
