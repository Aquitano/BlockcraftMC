package me.blockcraft.sternenfront.appearance;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class RigPose {

    private RigPose() { }

    static Transformation at(float yaw, float x, float y, float z, float pitch, float scale) {
        var rotation = new Quaternionf().rotationY(yaw);
        var offset = rotation.transform(new Vector3f(x, y, z));
        rotation.rotateX(pitch);
        // Minecraft's item-display renderer adds a half turn after this transformation.
        return new Transformation(offset, rotation, new Vector3f(scale), new Quaternionf().rotationY((float) Math.PI));
    }
}
