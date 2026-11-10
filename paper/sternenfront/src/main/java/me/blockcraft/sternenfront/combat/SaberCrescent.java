package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class SaberCrescent {

    private static final double MAX_ANGLE = Math.toRadians(36);
    private static final double REACH_TOLERANCE = 0.12;
    private static final double MODEL_HALF_WIDTH = 0.3;
    private static final double MODEL_RISE = 0.1;

    record Sample(Vector direction, double reach) { }

    record Segment(Vector first, Vector last, double reach) {
        Quaternionf turn(Vector normal) {
            var middle = first.clone().add(last).normalize();
            return Flares.lying(normal, middle.crossProduct(normal), 0);
        }

        Vector3f scale() {
            double half = first.angle(last) / 2;
            return new Vector3f((float) (reach * Math.sin(half) / MODEL_HALF_WIDTH),
                    (float) (reach * (1 - Math.cos(half)) / MODEL_RISE), 1);
        }

        Vector offset() {
            return first.clone().add(last).normalize().multiply(reach - scale().y);
        }
    }

    private SaberCrescent() { }

    static List<Segment> segments(List<Sample> samples) {
        var segments = new ArrayList<Segment>();
        int start = 0;
        while (start < samples.size() - 1) {
            var first = samples.get(start);
            double near = first.reach();
            double far = near;
            int end = start;
            while (end + 1 < samples.size()) {
                var next = samples.get(end + 1);
                double nextNear = Math.min(near, next.reach());
                double nextFar = Math.max(far, next.reach());
                if (first.direction().angle(next.direction()) > MAX_ANGLE || nextFar - nextNear > REACH_TOLERANCE) {
                    break;
                }
                near = nextNear;
                far = nextFar;
                end++;
            }
            if (end > start && near > 0.4) {
                segments.add(new Segment(first.direction(), samples.get(end).direction(), near));
            }
            start = end > start ? end : start + 1;
        }
        return segments;
    }
}
