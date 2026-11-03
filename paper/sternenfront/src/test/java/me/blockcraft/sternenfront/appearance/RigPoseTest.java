package me.blockcraft.sternenfront.appearance;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RigPoseTest {

    @Test
    public void renderedMuzzleFacesTheAimAfterMinecraftRotatesTheItemDisplay() {
        for (float yaw : new float[] {0, 1.4f, -2.7f}) {
            for (float pitch : new float[] {0, 0.5f, -0.7f}) {
                var pose = RigPose.at(yaw, -0.2f, 1.27f, 0.53f, pitch, 0.65f);
                var rendered = new Quaternionf().rotationY((float) Math.PI).transform(new Vector3f(0, 0, 1));
                pose.getRightRotation().transform(rendered);
                pose.getLeftRotation().transform(rendered);
                var aim = new Quaternionf().rotationY(yaw).rotateX(pitch).transform(new Vector3f(0, 0, 1));
                assertEquals(0, rendered.distance(aim), 0.00001);
            }
        }
    }
}
