package me.blockcraft.sternenfront.appearance;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class RigPose {

    private RigPose() { }

    static float turn(float current, float wanted, float degrees) {
        double delta = Math.atan2(Math.sin(wanted - current), Math.cos(wanted - current));
        double limit = Math.toRadians(degrees);
        if (Math.abs(delta) <= limit) {
            return (float) Math.atan2(Math.sin(wanted), Math.cos(wanted));
        }
        double next = current + Math.clamp(delta, -limit, limit);
        return (float) Math.atan2(Math.sin(next), Math.cos(next));
    }

    static Transformation at(float anchorYaw, float yaw, float x, float y, float z, float pitch, float scale) {
        return at(anchorYaw, yaw, x, y, 0, z, pitch, scale);
    }

    /** {@code x}, {@code y} and {@code bodyZ} sit in the body's frame, {@code aimZ} reaches along where the part points. */
    static Transformation at(float anchorYaw, float yaw, float x, float y, float bodyZ, float aimZ, float pitch, float scale) {
        var rotation = new Quaternionf().rotationY(yaw);
        var offset = new Quaternionf().rotationY(anchorYaw).transform(new Vector3f(x, y, bodyZ));
        offset.add(rotation.transform(new Vector3f(0, 0, aimZ)));
        rotation.rotateX(pitch);
        // Minecraft's item-display renderer adds a half turn after this transformation.
        return new Transformation(offset, rotation, new Vector3f(scale), new Quaternionf().rotationY((float) Math.PI));
    }
}
