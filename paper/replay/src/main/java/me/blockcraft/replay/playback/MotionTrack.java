package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Where the recorded players and entities are at any moment, and what they did. The recorder skips a sample in which
 * nothing changed, so the shortest gap between two frames is the sample interval; motion is spread over that interval
 * before the frame it ends in.
 */
final class MotionTrack {

    private static final double TELEPORT_DISTANCE_SQUARED = 8 * 8;

    private final List<ReplayEvent.Frame> frames;
    private final int sampleTicks;

    MotionTrack(List<ReplayEvent.Frame> frames) {
        this.frames = List.copyOf(frames);
        int shortest = Integer.MAX_VALUE;
        for (int i = 1; i < frames.size(); i++) {
            shortest = Math.min(shortest, frames.get(i).tick() - frames.get(i - 1).tick());
        }
        sampleTicks = Math.max(1, shortest == Integer.MAX_VALUE ? 1 : shortest);
    }

    List<ReplayEvent.Actor> actors(double time) {
        return at(time, ReplayEvent.Frame::actors);
    }

    List<ReplayEvent.Prop> props(double time) {
        return at(time, ReplayEvent.Frame::props);
    }

    /** The animations recorded after tick {@code from}, up to and including tick {@code to}. */
    List<ReplayEvent.Animation> animations(int from, int to) {
        if (to <= from) {
            return List.of();
        }
        return frames.subList(frameAt(from) + 1, frameAt(to) + 1).stream()
                .flatMap(frame -> frame.animations().stream()).toList();
    }

    private <T extends ReplayEvent.Body<T>> List<T> at(double time, Function<ReplayEvent.Frame, List<T>> bodies) {
        int index = frameAt(time);
        if (index < 0) {
            return List.of();
        }
        var frame = frames.get(index);
        if (index + 1 == frames.size()) {
            return bodies.apply(frame);
        }
        var next = frames.get(index + 1);
        double start = Math.max(frame.tick(), next.tick() - sampleTicks);
        if (time <= start) {
            return bodies.apply(frame);
        }
        double progress = (time - start) / (next.tick() - start);
        var following = new HashMap<UUID, T>();
        bodies.apply(next).forEach(body -> following.put(body.id(), body));
        return bodies.apply(frame).stream().map(body -> {
            var target = following.get(body.id());
            return target == null ? body : between(body, target, progress);
        }).toList();
    }

    private int frameAt(double time) {
        int low = 0;
        int high = frames.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (frames.get(middle).tick() <= time) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }

    private static <T extends ReplayEvent.Body<T>> T between(T from, T to, double progress) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        if (!from.world().equals(to.world()) || dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQUARED) {
            return from;
        }
        float yaw = from.yaw() + (float) (wrap(to.yaw() - from.yaw()) * progress);
        float pitch = from.pitch() + (float) ((to.pitch() - from.pitch()) * progress);
        return from.at(from.x() + dx * progress, from.y() + dy * progress, from.z() + dz * progress, yaw, pitch);
    }

    private static double wrap(double degrees) {
        double wrapped = degrees % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }
}
