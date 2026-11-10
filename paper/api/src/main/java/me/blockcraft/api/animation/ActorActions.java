package me.blockcraft.api.animation;

import java.util.Map;
import java.util.function.Consumer;

public final class ActorActions {
    private final Map<String, Consumer<AnimationClip.Pose>> bones;
    private AnimationClip clip;
    private double started;

    public ActorActions(Map<String, Consumer<AnimationClip.Pose>> bones) {
        this.bones = Map.copyOf(bones);
    }

    public boolean play(AnimationClip next, double atSeconds) {
        if (!bones.keySet().containsAll(next.bones().keySet())) return false;
        reset();
        clip = next;
        started = atSeconds;
        tick(atSeconds);
        return true;
    }

    public void tick(double atSeconds) {
        if (clip == null) return;
        double time = clip.time(atSeconds - started);
        clip.bones().forEach((name, bone) -> bones.get(name).accept(bone.sample(time)));
    }

    public void reset() {
        clip = null;
        var rest = new AnimationClip.Pose(AnimationClip.Vector.ZERO, AnimationClip.Vector.ZERO, AnimationClip.Vector.ONE);
        bones.values().forEach(bone -> bone.accept(rest));
    }
}
