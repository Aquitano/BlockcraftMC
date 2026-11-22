package me.blockcraft.zombies.game;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * A BetterModel rig drawn over an enemy, sized in blocks at scale 1: {@code width} across, {@code length} from head to
 * tail, and the front {@code head} of that length is its head. The mob beneath keeps a small hitbox so it still fits
 * through the windows, so shots are tested against the model's size instead of the mob's. {@code mobScale} sizes the
 * mob beneath.
 */
record CreatureBody(String model, double width, double height, double length, double head, double mobScale) {

    static Vector facing(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0, Math.cos(radians));
    }

    /** The yaw that turns someone at {@code from} towards {@code to}; none when they stand on the same spot. */
    static Optional<Float> yaw(Location from, Location to) {
        var toward = to.toVector().subtract(from.toVector()).setY(0);
        return toward.lengthSquared() == 0 ? Optional.empty() : Optional.of((float) Math.toDegrees(Math.atan2(-toward.getX(), toward.getZ())));
    }

    /** The box around the model turned to {@code yaw}, standing at {@code feet}. */
    BoundingBox bounds(Vector feet, float yaw, double scale) {
        var facing = facing(yaw);
        double x = (Math.abs(facing.getX()) * length + Math.abs(facing.getZ()) * width) * scale / 2;
        double z = (Math.abs(facing.getZ()) * length + Math.abs(facing.getX()) * width) * scale / 2;
        return new BoundingBox(feet.getX() - x, feet.getY(), feet.getZ() - z, feet.getX() + x, feet.getY() + height * scale, feet.getZ() + z);
    }

    /** Whether {@code hit} lies in the front {@code head} of the model's length. */
    boolean isHead(Vector feet, float yaw, double scale, Vector hit) {
        double ahead = hit.clone().subtract(feet).setY(0).dot(facing(yaw));
        return ahead >= (length / 2 - head) * scale;
    }
}
