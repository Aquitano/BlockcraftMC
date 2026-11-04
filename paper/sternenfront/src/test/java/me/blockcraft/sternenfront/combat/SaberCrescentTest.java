package me.blockcraft.sternenfront.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SaberCrescentTest {

    @Test
    public void eachUnobstructedFrameIsOneContinuousLine() {
        var sweep = new SaberSweep(new Location(null, 0, 0, 0), Color.BLUE, 10, 3.5, 120, 4, 0, false);
        for (int tick = 0; tick < 4; tick++) {
            var samples = new ArrayList<SaberCrescent.Sample>();
            sweep.trailStart().ifPresent(ray -> samples.add(new SaberCrescent.Sample(ray, 3.5)));
            sweep.advance(tick).forEach(ray -> samples.add(new SaberCrescent.Sample(ray, 3.5)));
            assertEquals(1, SaberCrescent.segments(samples).size());
        }
    }

    @Test
    public void modelPointsAlongTheSweepAndStaysInsideItsReachAtEveryPitch() {
        for (float pitch : new float[] {-90, -45, 0, 45, 90}) {
            var origin = new Location(null, 0, 0, 0, 73, pitch);
            double yaw = Math.toRadians(origin.getYaw());
            var side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
            var normal = side.clone().crossProduct(origin.getDirection()).normalize();
            for (boolean reverse : new boolean[] {false, true}) {
                var rays = CombatArc.rays(origin.getDirection(), side, 3.5, 120, reverse);
                var samples = rays.stream().map(ray -> new SaberCrescent.Sample(ray, 3.5)).toList();
                for (var segment : SaberCrescent.segments(samples)) {
                    var middle = segment.first().clone().add(segment.last()).normalize().toVector3f();
                    var tip = segment.turn(normal).transform(new Vector3f(0, 1, 0).mul(segment.scale()))
                            .add(segment.offset().toVector3f());
                    assertEquals(3.5, tip.length(), 1e-5);
                    assertEquals(1, tip.normalize().dot(middle), 1e-5);
                    for (int sign : new int[] {-1, 1}) {
                        var edge = new Vector3f(sign * 0.3f, 0.9f, 0).mul(segment.scale());
                        segment.turn(normal).transform(edge).add(segment.offset().toVector3f());
                        assertEquals(segment.reach(), edge.length(), 1e-5);
                        assertEquals(segment.first().angle(segment.last()) / 2, edge.angle(middle), 1e-4);
                        assertEquals(0, edge.dot(normal.toVector3f()), 1e-5);
                    }
                    for (int point = -16; point <= 16; point++) {
                        float across = point * 0.3f / 16;
                        var at = new Vector3f(across, 1 - 0.1f * across * across / 0.09f, 0).mul(segment.scale());
                        segment.turn(normal).transform(at).add(segment.offset().toVector3f());
                        assertTrue(at.length() <= 3.50001);
                    }
                }
            }
        }
    }

    @Test
    public void wallStepsSplitTheTrailAndEachPieceUsesItsShortestReach() {
        var samples = List.of(sample(-15, 3.5), sample(-10, 3.42), sample(-5, 1), sample(0, 1),
                sample(5, 0), sample(10, 3.5), sample(15, 3.5));
        var segments = SaberCrescent.segments(samples);
        assertEquals(3, segments.size());
        assertEquals(3.42, segments.get(0).reach(), 1e-9);
        assertEquals(1, segments.get(1).reach(), 1e-9);
        assertEquals(3.5, segments.get(2).reach(), 1e-9);
        assertEquals(samples.get(3).direction(), segments.get(1).last());
        assertEquals(samples.get(5).direction(), segments.get(2).first());
    }

    private static SaberCrescent.Sample sample(double angle, double reach) {
        return new SaberCrescent.Sample(new Vector(Math.sin(Math.toRadians(angle)), 0, Math.cos(Math.toRadians(angle))), reach);
    }
}
