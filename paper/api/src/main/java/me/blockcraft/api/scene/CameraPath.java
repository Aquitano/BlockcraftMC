package me.blockcraft.api.scene;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Keyframes grouped into shots: a keyframe marked {@code cut} holds the previous shot until its tick and then starts a
 * new one. Within a shot the camera follows a monotone cubic through its keyframes, which never swings past a keyframe
 * on any axis however unevenly they are spaced, and its clock eases in over the shot's first {@link #EASE_TICKS} and out
 * over its last, so the camera starts and stops once per shot and keeps its pace through every keyframe between.
 * <p>
 * Between two keyframes that both {@link Look look} somewhere, the camera turns to that point at every tick, so the
 * subject stays framed while the camera moves. Fixed points follow their own curve; a point that moves, such as an
 * actor's head, is asked for where it is at that very tick.
 */
public record CameraPath(List<Keyframe> frames) {
    static final int EASE_TICKS = 20;

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

    /** Where the camera looks at a tick of the scene. */
    @FunctionalInterface
    public interface Look {
        Target at(int tick);
    }

    public record Target(double x, double y, double z) implements Look {
        public static Target of(Location location) {
            return new Target(location.getX(), location.getY(), location.getZ());
        }

        @Override
        public Target at(int tick) {
            return this;
        }
    }

    public record Keyframe(int tick, Pose pose, boolean cut, @Nullable Look look) {
        public Keyframe(int tick, Pose pose, boolean cut) {
            this(tick, pose, cut, null);
        }

        /** A keyframe at {@code at} that looks at {@code look}. */
        public static Keyframe aimed(int tick, Location at, Location look, boolean cut) {
            return aimed(tick, at, Target.of(look), cut);
        }

        /** A keyframe at {@code at} that looks wherever {@code look} is at each tick. */
        public static Keyframe aimed(int tick, Location at, Look look, boolean cut) {
            var target = look.at(tick);
            var pose = at.clone().setDirection(new Vector(target.x() - at.getX(), target.y() - at.getY(), target.z() - at.getZ()));
            return new Keyframe(tick, Pose.of(pose), cut, look);
        }
    }

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
        if (frames.get(to).cut()) return frames.get(to - 1).pose();
        int first = to - 1;
        while (!startsShot(first)) first--;
        int last = to;
        while (!endsShot(last)) last++;
        double time = eased(tick, frames.get(first).tick(), frames.get(last).tick());
        int segment = first;
        while (segment + 1 < last && frames.get(segment + 1).tick() <= time) segment++;
        double[] p = curve(first, last, segment, time, this::components);
        var from = frames.get(segment).look();
        var next = frames.get(segment + 1).look();
        if (from != null && next != null) {
            double[] target;
            if (from instanceof Target && next instanceof Target) {
                target = curve(first, last, segment, time, index -> frames.get(index).look() instanceof Target fixed ? point(fixed) : null);
            } else {
                double u = (time - frames.get(segment).tick()) / (frames.get(segment + 1).tick() - frames.get(segment).tick());
                double blend = u * u * (3 - 2 * u);
                double[] a = point(from.at(tick));
                double[] b = point(next.at(tick));
                target = new double[] {a[0] + (b[0] - a[0]) * blend, a[1] + (b[1] - a[1]) * blend, a[2] + (b[2] - a[2]) * blend};
            }
            if (target != null) aim(p, target);
        }
        return new Pose(p[0], p[1], p[2], (float) Math.IEEEremainder(p[3], 360), (float) Math.clamp(p[4], -90, 90));
    }

    /** The shot's own clock at {@code tick}, which arrives at {@code end} exactly when the shot does. */
    private static double eased(double tick, double start, double end) {
        double length = end - start;
        return start + Ramp.along(tick - start, length, Math.min(EASE_TICKS, length / 3));
    }

    private static void aim(double[] pose, double[] target) {
        double dx = target[0] - pose[0];
        double dy = target[1] - pose[1];
        double dz = target[2] - pose[2];
        double flat = Math.hypot(dx, dz);
        if (flat < 1e-6 && Math.abs(dy) < 1e-6) return;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        pose[3] += Math.IEEEremainder(yaw - pose[3], 360);
        pose[4] = Math.toDegrees(-Math.atan2(dy, flat));
    }

    private static double[] point(Target target) {
        return new double[] {target.x(), target.y(), target.z()};
    }

    private interface Values {
        double @Nullable [] at(int index);
    }

    /**
     * The monotone cubic through the shot's keyframes from {@code first} to {@code last}, at {@code time} within the
     * segment that starts at keyframe {@code segment}, or null where a keyframe there has no value. Each knot's slope is
     * the weighted harmonic mean of the secants beside it, and zero where they disagree in sign, as in Fritsch and
     * Carlson's method; the shot's first and last knots take their one secant.
     */
    private double @Nullable [] curve(int first, int last, int segment, double time, Values values) {
        double[] a = values.at(segment);
        double[] b = values.at(segment + 1);
        if (a == null || b == null) return null;
        double span = frames.get(segment + 1).tick() - frames.get(segment).tick();
        double u = (time - frames.get(segment).tick()) / span;
        double h00 = 2 * u * u * u - 3 * u * u + 1;
        double h10 = u * u * u - 2 * u * u + u;
        double h01 = -2 * u * u * u + 3 * u * u;
        double h11 = u * u * u - u * u;
        double[] before = segment > first ? values.at(segment - 1) : null;
        double[] after = segment + 1 < last ? values.at(segment + 2) : null;
        double[] result = new double[a.length];
        for (int axis = 0; axis < a.length; axis++) {
            double secant = (b[axis] - a[axis]) / span;
            double start = before == null ? secant
                    : slope((a[axis] - before[axis]) / (frames.get(segment).tick() - frames.get(segment - 1).tick()), secant,
                    frames.get(segment).tick() - frames.get(segment - 1).tick(), span);
            double end = after == null ? secant
                    : slope(secant, (after[axis] - b[axis]) / (frames.get(segment + 2).tick() - frames.get(segment + 1).tick()),
                    span, frames.get(segment + 2).tick() - frames.get(segment + 1).tick());
            result[axis] = h00 * a[axis] + h10 * span * start + h01 * b[axis] + h11 * span * end;
        }
        return result;
    }

    private static double slope(double left, double right, double leftSpan, double rightSpan) {
        if (left * right <= 0) return 0;
        double w1 = 2 * rightSpan + leftSpan;
        double w2 = rightSpan + 2 * leftSpan;
        return (w1 + w2) / (w1 / left + w2 / right);
    }

    private boolean startsShot(int index) {
        return index == 0 || frames.get(index).cut();
    }

    private boolean endsShot(int index) {
        return index == frames.size() - 1 || frames.get(index + 1).cut();
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
