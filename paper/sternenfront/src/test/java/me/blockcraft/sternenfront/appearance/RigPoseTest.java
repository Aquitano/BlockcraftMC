package me.blockcraft.sternenfront.appearance;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RigPoseTest {

    @Test
    public void bodyTurnsAreBoundedAndUseTheShortestPathAcrossTheYawBoundary() {
        assertEquals(12, Math.abs(Math.toDegrees(RigPose.turn(0, (float) Math.PI, 12))), 0.001);
        float turned = RigPose.turn((float) Math.toRadians(179), (float) Math.toRadians(-179), 12);
        assertEquals(-179, Math.toDegrees(turned), 0.001);
        for (int tick = 0; tick < 20; tick++) {
            assertEquals(turned, RigPose.turn(turned, (float) Math.toRadians(-179), 12), 0);
        }
    }

    @Test
    public void renderedMuzzleFacesTheAimAfterMinecraftRotatesTheItemDisplay() {
        for (float yaw : new float[] {0, 1.4f, -2.7f}) {
            for (float pitch : new float[] {0, 0.5f, -0.7f}) {
                float bodyYaw = yaw - 1.2f;
                var pose = RigPose.at(bodyYaw, yaw, -0.2f, 1.27f, 0.53f, pitch, 0.65f);
                var rendered = new Quaternionf().rotationY((float) Math.PI).transform(new Vector3f(0, 0, 1));
                pose.getRightRotation().transform(rendered);
                pose.getLeftRotation().transform(rendered);
                var aim = new Quaternionf().rotationY(yaw).rotateX(pitch).transform(new Vector3f(0, 0, 1));
                assertEquals(0, rendered.distance(aim), 0.00001);
                var shoulder = new Quaternionf().rotationY(bodyYaw).transform(new Vector3f(-0.2f, 1.27f, 0));
                var reach = new Quaternionf().rotationY(yaw).transform(new Vector3f(0, 0, 0.53f));
                assertEquals(0, pose.getTranslation().distance(shoulder.add(reach)), 0.00001);
            }
        }
    }
}
