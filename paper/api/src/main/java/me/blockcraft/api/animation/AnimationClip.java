package me.blockcraft.api.animation;

import java.util.List;
import java.util.Map;

public record AnimationClip(double seconds, boolean loop, Map<String, Bone> bones) {
    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
        public static final Vector ONE = new Vector(1, 1, 1);
        public Vector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite animation vector");
        }
        Vector toward(Vector other, double progress) {
            return new Vector(x + (other.x - x) * progress, y + (other.y - y) * progress, z + (other.z - z) * progress);
        }
    }
    public record Keyframe(double seconds, Vector value) { }
    public record Track(List<Keyframe> frames) {
        public Track {
            frames = List.copyOf(frames);
            if (frames.isEmpty()) throw new IllegalArgumentException("Empty animation track");
            double previous = -1;
            for (var frame : frames) {
                if (!Double.isFinite(frame.seconds()) || frame.seconds() <= previous || frame.seconds() < 0) {
                    throw new IllegalArgumentException("Animation keys must have increasing non-negative times");
                }
                previous = frame.seconds();
            }
        }
        public Vector sample(double seconds) {
            if (seconds <= frames.getFirst().seconds()) return frames.getFirst().value();
            for (int i = 1; i < frames.size(); i++) {
                var end = frames.get(i);
                if (seconds < end.seconds()) {
                    var start = frames.get(i - 1);
                    return start.value().toward(end.value(), (seconds - start.seconds()) / (end.seconds() - start.seconds()));
                }
            }
            return frames.getLast().value();
        }
        public static Track constant(Vector value) {
            return new Track(List.of(new Keyframe(0, value)));
        }
    }
    public record Bone(Track position, Track rotation, Track scale) {
        public Pose sample(double seconds) {
            return new Pose(position.sample(seconds), rotation.sample(seconds), scale.sample(seconds));
        }
    }
    public record Pose(Vector position, Vector rotation, Vector scale) { }

    public AnimationClip {
        if (!Double.isFinite(seconds) || seconds <= 0) throw new IllegalArgumentException("Animation duration must be positive");
        bones = Map.copyOf(bones);
        for (var bone : bones.values()) {
            if (List.of(bone.position(), bone.rotation(), bone.scale()).stream()
                    .anyMatch(track -> track.frames().getLast().seconds() > seconds)) {
                throw new IllegalArgumentException("Animation key exceeds duration");
            }
        }
    }

    public double time(double elapsed) {
        double positive = Math.max(0, elapsed);
        return loop ? positive % seconds : Math.min(seconds, positive);
    }
}
