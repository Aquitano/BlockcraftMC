package me.blockcraft.api.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Set;

public final class BedrockAnimations {
    public sealed interface Result {
        record Loaded(AnimationClip clip) implements Result { }
        record Invalid(String reason) implements Result { }
    }

    private BedrockAnimations() { }

    public static Result read(Reader reader, String animation) {
        try {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var animations = root.getAsJsonObject("animations");
            if (animations == null || !animations.has(animation)) return new Result.Invalid("Missing animation: " + animation);
            var source = animations.getAsJsonObject(animation);
            allowed(source, Set.of("loop", "animation_length", "bones"));
            boolean loop = source.has("loop") && source.get("loop").getAsBoolean();
            if (source.has("loop") && !source.get("loop").getAsJsonPrimitive().isBoolean()) {
                return new Result.Invalid("Only boolean animation loop is supported");
            }
            double length = source.get("animation_length").getAsDouble();
            var bones = new LinkedHashMap<String, AnimationClip.Bone>();
            for (var entry : source.getAsJsonObject("bones").entrySet()) {
                var bone = entry.getValue().getAsJsonObject();
                allowed(bone, Set.of("rotation", "position", "scale"));
                bones.put(entry.getKey(), new AnimationClip.Bone(track(bone.get("position"), AnimationClip.Vector.ZERO),
                        track(bone.get("rotation"), AnimationClip.Vector.ZERO), track(bone.get("scale"), AnimationClip.Vector.ONE)));
            }
            if (bones.isEmpty()) return new Result.Invalid("Animation has no bones");
            return new Result.Loaded(new AnimationClip(length, loop, bones));
        } catch (RuntimeException malformed) {
            return new Result.Invalid("Unsupported or malformed animation: " + malformed.getMessage());
        }
    }

    private static AnimationClip.Track track(JsonElement value, AnimationClip.Vector fallback) {
        if (value == null) return AnimationClip.Track.constant(fallback);
        if (value.isJsonArray()) return AnimationClip.Track.constant(vector(value));
        var frames = new ArrayList<AnimationClip.Keyframe>();
        for (var entry : value.getAsJsonObject().entrySet()) {
            var frame = entry.getValue();
            if (frame.isJsonObject()) {
                var keyed = frame.getAsJsonObject();
                allowed(keyed, Set.of("post", "lerp_mode"));
                if (keyed.has("lerp_mode") && !keyed.get("lerp_mode").getAsString().equals("linear")) {
                    throw new IllegalArgumentException("Only linear interpolation is supported");
                }
                frame = keyed.get("post");
            }
            frames.add(new AnimationClip.Keyframe(Double.parseDouble(entry.getKey()), vector(frame)));
        }
        frames.sort(Comparator.comparingDouble(AnimationClip.Keyframe::seconds));
        return new AnimationClip.Track(frames);
    }

    private static AnimationClip.Vector vector(JsonElement value) {
        var array = value.getAsJsonArray();
        if (array.size() != 3) throw new IllegalArgumentException("Animation vector needs three numeric components");
        for (var element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Molang expressions are not supported");
            }
        }
        return new AnimationClip.Vector(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static void allowed(JsonObject object, Set<String> supported) {
        for (var key : object.keySet()) {
            if (!supported.contains(key)) throw new IllegalArgumentException("Unsupported animation property: " + key);
        }
    }
}
