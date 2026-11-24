package me.blockcraft.api.animation;

import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BedrockAnimationsTest {
    private static BedrockAnimations.Result read(String bone) {
        return BedrockAnimations.read(new StringReader("""
                {"format_version":"1.8.0","animations":{"wave":{"animation_length":2,"loop":true,"bones":{"arm":%s}}}}
                """.formatted(bone)), "wave");
    }

    @Test
    public void importsNumericBlockbenchKeysAndInterpolatesLoopingActorActions() {
        var result = read("""
                {"rotation":{"0":[0,0,0],"1":{"post":[-90,0,0],"lerp_mode":"linear"},"2":[0,0,0]}}
                """);
        assertTrue(result instanceof BedrockAnimations.Result.Loaded);
        var clip = ((BedrockAnimations.Result.Loaded) result).clip();
        var poses = new ArrayList<AnimationClip.Pose>();
        var actor = new ActorActions(Map.of("arm", poses::add));
        assertTrue(actor.play(clip, 5));
        actor.tick(5.5);
        assertEquals(-45, poses.getLast().rotation().x(), 0.001);
        actor.tick(7.5);
        assertEquals(-45, poses.getLast().rotation().x(), 0.001);
        actor.reset();
        assertEquals(AnimationClip.Vector.ZERO, poses.getLast().rotation());
        assertEquals(AnimationClip.Vector.ONE, poses.getLast().scale());
        assertFalse(new ActorActions(Map.of()).play(clip, 0));
    }

    @Test
    public void rejectsUnsupportedExpressionsAndCurvesInsteadOfSilentlyPlayingDifferentAnimation() {
        assertTrue(read("{\"rotation\":[\"query.life_time\",0,0]}") instanceof BedrockAnimations.Result.Invalid);
        assertTrue(read("{\"rotation\":{\"0\":{\"post\":[0,0,0],\"lerp_mode\":\"catmullrom\"}}}") instanceof BedrockAnimations.Result.Invalid);
        assertTrue(read("{\"rotation\":{\"3\":[0,0,0]}}") instanceof BedrockAnimations.Result.Invalid);
    }
}
