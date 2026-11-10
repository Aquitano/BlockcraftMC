package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;

import java.util.Optional;

public enum DamageDirection {
    FRONT, BACK, LEFT, RIGHT;

    public static Optional<DamageDirection> from(Vector facing, Vector toward) {
        var forward = facing.clone().setY(0);
        var source = toward.clone().setY(0);
        if (forward.lengthSquared() < 0.001 || source.lengthSquared() < 0.001) {
            return Optional.empty();
        }
        forward.normalize();
        source.normalize();
        double ahead = forward.dot(source);
        double left = forward.getZ() * source.getX() - forward.getX() * source.getZ();
        return Optional.of(Math.abs(ahead) >= Math.abs(left) ? ahead >= 0 ? FRONT : BACK : left >= 0 ? LEFT : RIGHT);
    }
}
