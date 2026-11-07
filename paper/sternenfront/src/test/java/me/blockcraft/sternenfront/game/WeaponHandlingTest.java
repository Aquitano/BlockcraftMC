package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WeaponHandlingTest {

    private static final WeaponHandling HANDLING = new WeaponHandling(2, 0.5, 1, 0.5, 0.6);

    @Test
    public void movingAndHeatOpenTheAimAndCrouchingTightensIt() {
        assertEquals(0.01, HANDLING.spread(0.01, false, false, 0), 1e-9);
        assertEquals(0.02, HANDLING.spread(0.01, true, false, 0), 1e-9);
        assertEquals(0.005, HANDLING.spread(0.01, false, true, 0), 1e-9);
        assertEquals(0.02, HANDLING.spread(0.01, false, false, 1), 1e-9);
    }

    @Test
    public void damageHoldsUpToTheFalloffStartThenThinsToTheMinimum() {
        assertEquals(10, HANDLING.damage(10, 40, 100), 1e-9);
        assertEquals(8, HANDLING.damage(10, 75, 100), 1e-9);
        assertEquals(6, HANDLING.damage(10, 100, 100), 1e-9);
    }
}
