package me.blockcraft.api.scene;

import org.bukkit.Location;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CameraPathTest {
    @Test
    public void interpolatesAcrossTheShortYawArcAndClampsAtEndpoints() {
        var path = new CameraPath(List.of(
                new CameraPath.Keyframe(0, new CameraPath.Pose(0, 20, 0, 170, 0), false),
                new CameraPath.Keyframe(20, new CameraPath.Pose(10, 30, 10, -170, 20), false)));
        var halfway = path.sample(10);
        assertEquals(5, halfway.x(), 0.0001);
        assertEquals(25, halfway.y(), 0.0001);
        assertEquals(180, halfway.yaw(), 0.0001);
        assertEquals(10, halfway.pitch(), 0.0001);
        assertEquals(path.frames().getFirst().pose(), path.sample(-10));
        assertEquals(path.frames().getLast().pose(), path.sample(30));
    }

    @Test
    public void holdsThePreviousShotUntilACut() {
        var first = new CameraPath.Pose(0, 0, 0, 0, 0);
        var second = new CameraPath.Pose(100, 0, 0, 0, 0);
        var path = new CameraPath(List.of(new CameraPath.Keyframe(0, first, false), new CameraPath.Keyframe(20, second, true)));
        assertEquals(first, path.sample(19));
        assertEquals(second, path.sample(20));
    }

    @Test
    public void keepsItsPaceThroughAnInteriorKeyframeAndEasesOnlyAtTheShotsEnds() {
        var path = new CameraPath(List.of(
                new CameraPath.Keyframe(0, new CameraPath.Pose(0, 0, 0, 0, 0), false),
                new CameraPath.Keyframe(30, new CameraPath.Pose(15, 0, 0, 0, 0), false),
                new CameraPath.Keyframe(60, new CameraPath.Pose(30, 0, 0, 0, 0), false)));
        double cruise = path.sample(30).x() - path.sample(29).x();
        assertEquals(15, path.sample(30).x(), 0.0001);
        assertEquals(cruise, path.sample(31).x() - path.sample(30).x(), 0.0001);
        assertEquals(cruise, path.sample(25).x() - path.sample(24).x(), 0.0001);
        assertTrue(path.sample(1).x() - path.sample(0).x() < cruise / 10);
    }

    @Test
    public void neverSwingsPastAKeyframeWhenTheyAreUnevenlySpaced() {
        var path = new CameraPath(List.of(
                new CameraPath.Keyframe(0, new CameraPath.Pose(0, 0, 0, 0, 0), false),
                new CameraPath.Keyframe(10, new CameraPath.Pose(20, 0, 0, 0, 0), false),
                new CameraPath.Keyframe(60, new CameraPath.Pose(21, 0, 0, 0, 0), false)));
        for (int tick = 1; tick <= 60; tick++) {
            assertTrue(path.sample(tick).x() >= path.sample(tick - 1).x());
            assertTrue(path.sample(tick).x() <= 21);
        }
    }

    @Test
    public void followsASubjectThatMovesBetweenKeyframes() {
        CameraPath.Look walker = tick -> new CameraPath.Target(tick, 0, 10);
        var path = new CameraPath(List.of(
                CameraPath.Keyframe.aimed(0, new Location(null, 0, 0, 0), walker, false),
                CameraPath.Keyframe.aimed(40, new Location(null, 0, 0, 0), walker, false)));
        assertEquals(Math.toDegrees(Math.atan2(-15, 10)), path.sample(15).yaw(), 0.01);
    }

    @Test
    public void keepsLookingAtItsTargetBetweenKeyframes() {
        var look = new Location(null, 10, 0, 10);
        var path = new CameraPath(List.of(
                CameraPath.Keyframe.aimed(0, new Location(null, 0, 0, 0), look, false),
                CameraPath.Keyframe.aimed(20, new Location(null, 20, 0, 0), look, false)));
        var pose = path.sample(5);
        assertEquals(Math.toDegrees(Math.atan2(-(10 - pose.x()), 10)), pose.yaw(), 0.01);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRepeatedFrameTimes() {
        var pose = new CameraPath.Pose(0, 0, 0, 0, 0);
        new CameraPath(List.of(new CameraPath.Keyframe(0, pose, false), new CameraPath.Keyframe(0, pose, false)));
    }
}
