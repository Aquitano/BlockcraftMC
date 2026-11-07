package me.blockcraft.sternenfront.game;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Optional;

/** One fighter's hitbox and eye height over the last few ticks, the oldest overwritten first. */
final class PoseHistory {

    record Pose(BoundingBox box, double eyeY) {

        Pose toward(Pose later, double share) {
            var min = box.getMin();
            var max = box.getMax();
            return new Pose(BoundingBox.of(min.add(later.box.getMin().subtract(min).multiply(share)),
                    max.add(later.box.getMax().subtract(max).multiply(share))), eyeY + (later.eyeY - eyeY) * share);
        }

        Optional<Vector> hit(Vector start, Vector direction, double reach, double margin) {
            return Optional.ofNullable(box.clone().expand(margin).rayTrace(start, direction, reach)).map(RayTraceResult::getHitPosition);
        }
    }

    private final Pose[] poses;
    private long newest;
    private int size;

    PoseHistory(int capacity, long tick, Pose first) {
        poses = new Pose[capacity];
        record(tick, first);
    }

    /** Ticks are kept in a row, so a missed one starts the history over. */
    void record(long tick, Pose pose) {
        if (size > 0 && tick != newest + 1) {
            size = 0;
        }
        newest = tick;
        poses[slot(tick)] = pose;
        size = Math.min(size + 1, poses.length);
    }

    /** The pose at a tick, between the two recorded around it, or the nearest end of what is kept. */
    Pose at(double tick) {
        double back = Math.clamp(newest - tick, 0, size - 1);
        long older = newest - (long) Math.ceil(back);
        long later = newest - (long) Math.floor(back);
        return poses[slot(older)].toward(poses[slot(later)], Math.ceil(back) - back);
    }

    private int slot(long tick) {
        return (int) Math.floorMod(tick, poses.length);
    }
}
