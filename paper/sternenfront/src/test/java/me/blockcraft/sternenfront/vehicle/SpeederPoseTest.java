package me.blockcraft.sternenfront.vehicle;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpeederPoseTest {

    private static Vector3f rendered(SpeederPose pose, Vector3f modelPoint) {
        var transformation = pose.transformation(0, 0);
        var point = new Quaternionf().rotationY((float) Math.PI).transform(new Vector3f(modelPoint));
        transformation.getRightRotation().transform(point);
        return transformation.getLeftRotation().transform(point);
    }

    @Test
    public void noseLeadsAfterMinecraftRotatesTheItemDisplayAndLeansIntoMotion() {
        assertEquals(0, rendered(SpeederPose.LEVEL, new Vector3f(0, 0, 1)).distance(new Vector3f(0, 0, 1)), 0.00001);
        assertTrue(rendered(new SpeederPose(0, 10), new Vector3f(0, 0, 1)).y > 0);
        // Facing +z the rider's right is -x, so a right turn lifts the +x side.
        assertTrue(rendered(new SpeederPose(10, 0), new Vector3f(1, 0, 0)).y > 0);
    }
}
