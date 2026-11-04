package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class SaberCrescent {

    private static final double MODEL_ANGLE = Math.toRadians(30);
    private static final double REACH_TOLERANCE = 0.12;

    record Sample(Vector direction, double reach) { }

    record Segment(Vector first, Vector last, double reach) {
        Quaternionf turn(Vector normal) {
            var middle = first.clone().add(last).normalize();
            return Flares.lying(normal, middle.crossProduct(normal), 0);
        }

        Vector3f scale() {
            double width = Math.tan(first.angle(last) / 2) / Math.tan(MODEL_ANGLE / 2);
            return new Vector3f((float) (reach * width), (float) reach, (float) reach);
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
                if (first.direction().angle(next.direction()) > MODEL_ANGLE || nextFar - nextNear > REACH_TOLERANCE) {
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
