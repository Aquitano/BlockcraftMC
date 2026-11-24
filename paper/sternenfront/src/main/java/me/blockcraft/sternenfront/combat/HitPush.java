package me.blockcraft.sternenfront.combat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * How far a bolt pushes whoever it hits along its flight ({@code combat.hit-push}): {@code perDamage} blocks a tick for
 * each point of damage, at most {@code most}.
 */
public record HitPush(double perDamage, double most) {

    /** Ground friction keeps about 0.55 of a walker's speed each tick, so a push carries about this many times its speed. */
    private static final double CARRY = 1 / (1 - 0.6 * 0.91);
    private static final double HALF_WIDTH = 0.3;
    private static final double BELOW_FEET = 0.5;

    public static HitPush from(ConfigurationSection config) {
        return new HitPush(Math.clamp(config.getDouble("combat.hit-push.per-damage"), 0, 0.1),
                Math.clamp(config.getDouble("combat.hit-push.max"), 0, 0.5));
    }

    /**
     * Along the level part of {@code direction}, for someone standing on the ground where ground goes on under the spot
     * the push carries them to: in the air or at an edge a push could throw them off, there is none.
     */
    public Vector of(LivingEntity victim, Vector direction, double damage) {
        double speed = Math.min(most, perDamage * damage);
        var level = new Vector(direction.getX(), 0, direction.getZ());
        if (speed <= 0 || level.lengthSquared() < 1e-6 || !victim.isOnGround() || victim.isInsideVehicle()) {
            return new Vector();
        }
        level.normalize();
        var landing = victim.getLocation().add(level.clone().multiply(speed * CARRY + HALF_WIDTH)).subtract(0, BELOW_FEET, 0);
        return landing.getBlock().isPassable() ? new Vector() : level.multiply(speed);
    }
}
