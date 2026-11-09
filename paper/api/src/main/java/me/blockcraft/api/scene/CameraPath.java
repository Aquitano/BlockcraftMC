package me.blockcraft.api.scene;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * Keyframes grouped into shots: a keyframe marked {@code cut} holds the previous shot until its tick and then starts a
 * new one. Within a shot the camera follows a cubic Hermite spline whose tangents come from the neighbouring keyframes,
 * so it glides through interior keyframes at an even speed and eases only where a shot starts and ends.
 */
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

    public boolean cutAt(int tick) {
        return frames.stream().anyMatch(frame -> frame.cut() && frame.tick() == tick);
    }

    /** The tick of the next cut after {@code tick}, or one past the end: the camera of the current shot lives until then. */
    public int shotEnd(int tick) {
        return frames.stream().filter(frame -> frame.cut() && frame.tick() > tick)
                .mapToInt(Keyframe::tick).findFirst().orElse(duration() + 1);
    }

    public Pose sample(int tick) {
        if (tick <= 0) return frames.getFirst().pose();
        if (tick >= duration()) return frames.getLast().pose();
        int to = 1;
        while (frames.get(to).tick() <= tick) to++;
        var from = frames.get(to - 1);
        var next = frames.get(to);
        if (next.cut()) return from.pose();
        double span = next.tick() - from.tick();
        double u = (tick - from.tick()) / span;
        double[] a = components(to - 1);
        double[] b = components(to);
        double[] ta = tangent(to - 1);
        double[] tb = tangent(to);
        double h00 = 2 * u * u * u - 3 * u * u + 1;
        double h10 = u * u * u - 2 * u * u + u;
        double h01 = -2 * u * u * u + 3 * u * u;
        double h11 = u * u * u - u * u;
        double[] p = new double[5];
        for (int axis = 0; axis < 5; axis++) {
            p[axis] = h00 * a[axis] + h10 * span * ta[axis] + h01 * b[axis] + h11 * span * tb[axis];
        }
        return new Pose(p[0], p[1], p[2], (float) p[3], (float) Math.clamp(p[4], -90, 90));
    }

    private boolean startsShot(int index) {
        return index == 0 || frames.get(index).cut();
    }

    private boolean endsShot(int index) {
        return index == frames.size() - 1 || frames.get(index + 1).cut();
    }

    /** Units per tick; zero where a shot starts or ends, so the camera eases in and out there. */
    private double[] tangent(int index) {
        if (startsShot(index) || endsShot(index)) return new double[5];
        double[] before = components(index - 1);
        double[] after = components(index + 1);
        double span = frames.get(index + 1).tick() - frames.get(index - 1).tick();
        double[] tangent = new double[5];
        for (int axis = 0; axis < 5; axis++) tangent[axis] = (after[axis] - before[axis]) / span;
        return tangent;
    }

    /** Position and rotation with the yaw unwound along its shot, so a turn through 180 degrees takes the short way. */
    private double[] components(int index) {
        int start = index;
        while (!startsShot(start)) start--;
        double yaw = frames.get(start).pose().yaw();
        for (int i = start + 1; i <= index; i++) {
            yaw += Math.IEEEremainder(frames.get(i).pose().yaw() - frames.get(i - 1).pose().yaw(), 360);
        }
        var pose = frames.get(index).pose();
        return new double[] {pose.x(), pose.y(), pose.z(), yaw, pose.pitch()};
    }
}
