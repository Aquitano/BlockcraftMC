package me.blockcraft.api.scene;

import org.bukkit.Location;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

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
    public void glidesThroughAnInteriorKeyframeWithoutStopping() {
        var path = new CameraPath(List.of(
                new CameraPath.Keyframe(0, new CameraPath.Pose(0, 0, 0, 0, 0), false),
                new CameraPath.Keyframe(20, new CameraPath.Pose(10, 0, 0, 0, 0), false),
                new CameraPath.Keyframe(40, new CameraPath.Pose(20, 0, 0, 0, 0), false)));
        assertEquals(10, path.sample(20).x(), 0.0001);
        assertEquals(0.5, path.sample(20).x() - path.sample(19).x(), 0.05);
        assertEquals(0.5, path.sample(21).x() - path.sample(20).x(), 0.05);
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
