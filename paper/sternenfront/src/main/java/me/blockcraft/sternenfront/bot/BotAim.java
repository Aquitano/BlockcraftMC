package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

public final class BotAim {

    private BotAim() { }

    public static Vector turn(Vector current, Vector wanted, double degrees) {
        var from = current.clone().normalize();
        var to = wanted.clone().normalize();
        double angle = from.angle(to);
        double limit = Math.toRadians(degrees);
        if (angle <= limit) {
            return to;
        }
        var axis = from.clone().crossProduct(to);
        if (axis.lengthSquared() < 0.00001) {
            axis = Math.abs(from.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        }
        return from.rotateAroundAxis(axis.normalize(), limit).normalize();
    }
}
