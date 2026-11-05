package me.blockcraft.api.scene;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

public record CameraPath(List<Keyframe> frames) {
    public record Pose(double x, double y, double z, float yaw, float pitch) {
        public Pose {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Camera coordinates must be finite");
            }
        }

        public static Pose of(Location location) {
            return new Pose(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        }

        public Location in(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public record Keyframe(int tick, Pose pose, boolean cut) { }

    public CameraPath {
        frames = List.copyOf(frames);
        if (frames.size() < 2 || frames.getFirst().tick() != 0) {
            throw new IllegalArgumentException("Camera path needs at least two frames starting at tick zero");
        }
        for (int i = 1; i < frames.size(); i++) {
            if (frames.get(i).tick() <= frames.get(i - 1).tick()) {
                throw new IllegalArgumentException("Camera frame ticks must increase");
            }
        }
    }

    public int duration() {
        return frames.getLast().tick();
    }

    public Pose sample(int tick) {
        if (tick <= 0) return frames.getFirst().pose();
        for (int i = 1; i < frames.size(); i++) {
            var to = frames.get(i);
            if (tick < to.tick()) {
                var from = frames.get(i - 1);
                if (to.cut()) return from.pose();
                double progress = (double) (tick - from.tick()) / (to.tick() - from.tick());
                double eased = progress * progress * (3 - 2 * progress);
                var a = from.pose();
                var b = to.pose();
                float turn = (float) Math.IEEEremainder(b.yaw() - a.yaw(), 360);
                return new Pose(mix(a.x(), b.x(), eased), mix(a.y(), b.y(), eased), mix(a.z(), b.z(), eased),
                        a.yaw() + turn * (float) eased, (float) mix(a.pitch(), b.pitch(), eased));
            }
        }
        return frames.getLast().pose();
    }

    private static double mix(double a, double b, double progress) {
        return a + (b - a) * progress;
    }
}
