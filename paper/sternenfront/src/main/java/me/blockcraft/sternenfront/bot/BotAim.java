package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

public final class BotAim {

    private BotAim() { }

    public static boolean moving(boolean wasMoving, Vector velocity) {
        return velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ()
                > (wasMoving ? 0.015 * 0.015 : 0.06 * 0.06);
    }

    /** How many times its settled spread a bot's aim scatters {@code since} ticks after it picked its target. */
    static double settle(long since, int ticks, double start) {
        return since >= ticks ? 1 : start - (start - 1) * since / ticks;
    }

    /**
     * How far beside {@code aim} a bot's shot goes {@code since} ticks after it picked its target: {@code error} per block
     * at first, toward {@code angle} round the aim, closing to nothing over {@code ticks}.
     */
    static Vector misaim(Vector aim, double angle, double error, long since, int ticks) {
        double left = ticks <= 0 ? 0 : Math.max(0, 1 - (double) since / ticks);
        var forward = aim.clone().normalize();
        var side = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1.0E-6) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        var up = side.clone().crossProduct(forward);
        return side.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle))).multiply(error * left);
    }

    /** Whether {@code aim} lies within {@code degrees} either side of where a body with {@code bodyYaw} faces. */
    static boolean covers(float bodyYaw, Vector aim, double degrees) {
        if (aim.getX() * aim.getX() + aim.getZ() * aim.getZ() < 1.0E-4) {
            return true;
        }
        return Math.abs(wrap(yaw(aim) - bodyYaw)) <= degrees;
    }

    /** A body's yaw turned toward {@code aim} by at most {@code degrees}. */
    static float turnYaw(float bodyYaw, Vector aim, float degrees) {
        float difference = wrap(yaw(aim) - bodyYaw);
        return wrap(bodyYaw + Math.clamp(difference, -degrees, degrees));
    }

    private static float yaw(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360;
        return wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped;
    }

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
