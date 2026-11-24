package me.blockcraft.sternenfront.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShipyardEffectsTest {

    @Test
    public void craneReturnsAlongItsRailAndSlowsAtBothEnds() {
        assertEquals(0, ShipyardEffects.travel(0, 600), 0.0001);
        assertEquals(1, ShipyardEffects.travel(300, 600), 0.0001);
        assertEquals(0, ShipyardEffects.travel(600, 600), 0.0001);
        assertEquals(ShipyardEffects.travel(100, 600), ShipyardEffects.travel(500, 600), 0.0001);
        double atEnd = ShipyardEffects.travel(4, 600) - ShipyardEffects.travel(0, 600);
        double atMiddle = ShipyardEffects.travel(154, 600) - ShipyardEffects.travel(150, 600);
        assertTrue(atEnd < atMiddle / 10);
    }
}
