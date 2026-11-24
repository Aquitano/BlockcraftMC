package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BlasterPresentationTest {

    @Test
    public void everyPoseKeepsTheWeaponsVanillaFallback() {
        for (var blaster : Blaster.values()) {
            for (var pose : Blaster.Pose.values()) {
                assertEquals(blaster.model().fallback(), blaster.model(pose).fallback());
            }
        }
    }
}
