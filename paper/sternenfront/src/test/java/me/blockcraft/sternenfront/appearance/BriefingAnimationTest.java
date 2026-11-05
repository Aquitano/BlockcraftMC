package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.animation.AnimationClip;
import me.blockcraft.api.animation.BedrockAnimations;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BriefingAnimationTest {
    private static AnimationClip briefing() throws Exception {
        try (var input = new InputStreamReader(BriefingAnimationTest.class.getResourceAsStream(
                "/animations/briefing.animation.json"), StandardCharsets.UTF_8)) {
            var result = BedrockAnimations.read(input, "animation.infantry.briefing");
            assertTrue(result instanceof BedrockAnimations.Result.Loaded);
            return ((BedrockAnimations.Result.Loaded) result).clip();
        }
    }

    @Test
    public void saluteAndHeadEaseAtTheirEndpointsAndReturnBeforeTheShotFades() throws Exception {
        var clip = briefing();
        var arm = clip.bones().get("right_arm").rotation();
        double raisingMiddle = Math.abs(arm.sample(0.3).x() - arm.sample(0.25).x());
        assertTrue(Math.abs(arm.sample(0.05).x()) < raisingMiddle / 3);
        assertTrue(Math.abs(arm.sample(0.55).x() - arm.sample(0.5).x()) < raisingMiddle / 3);
        assertEquals(-125, arm.sample(0.55).x(), 0.00001);
        assertEquals(arm.sample(0.55), arm.sample(1));
        double loweringMiddle = Math.abs(arm.sample(1.3).x() - arm.sample(1.25).x());
        assertTrue(Math.abs(arm.sample(1.05).x() - arm.sample(1).x()) < loweringMiddle / 3);
        assertTrue(Math.abs(arm.sample(1.45).x()) < loweringMiddle / 3);
        assertEquals(AnimationClip.Vector.ZERO, arm.sample(1.5));
        var head = clip.bones().get("head").rotation();
        double headMiddle = Math.abs(head.sample(0.4).y() - head.sample(0.35).y());
        assertTrue(Math.abs(head.sample(0.2).y()) < headMiddle / 3);
        assertTrue(Math.abs(head.sample(0.55).y() - head.sample(0.5).y()) < headMiddle / 3);
    }

    @Test
    public void loopingBriefingReturnsEveryBoneToRestWithoutASnap() throws Exception {
        var clip = briefing();
        assertTrue(clip.loop());
        assertEquals(4, clip.seconds(), 0);
        for (var bone : clip.bones().values()) {
            assertEquals(bone.sample(0), bone.sample(clip.seconds()));
            assertEquals(AnimationClip.Vector.ZERO, bone.sample(0).position());
            assertEquals(AnimationClip.Vector.ZERO, bone.sample(0).rotation());
            assertEquals(AnimationClip.Vector.ONE, bone.sample(0).scale());
            var before = bone.sample(3.95);
            assertEquals(0, before.rotation().x(), 0.00001);
            assertEquals(0, before.rotation().y(), 0.00001);
            assertEquals(0, before.position().y(), 0.002);
        }
    }
}
