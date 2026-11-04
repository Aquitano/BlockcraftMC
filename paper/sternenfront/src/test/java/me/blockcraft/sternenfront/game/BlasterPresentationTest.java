package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BlasterPresentationTest {

    @Test
    public void firingPosesKeepEachWeaponsVanillaFallback() {
        for (var blaster : Blaster.values()) {
            assertEquals(blaster.model().fallback(), blaster.firingModel().fallback());
        }
    }
}
