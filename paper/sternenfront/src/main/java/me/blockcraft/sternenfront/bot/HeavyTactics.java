package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

/** When a heavy bot spends its faction's signature ability, apart from the world it reads. */
final class HeavyTactics {

    private static final double SAFE_MARGIN = 1;
    private static final double LONGEST_THROW = 18;
    private static final double GRAVITY = 0.04;

    private HeavyTactics() {
    }

    /** Rooting itself only pays where it stands anyway, with something to shoot and a gun and body fit to stay. */
    static boolean sieges(boolean holdingPost, boolean enemyInRange, boolean venting, boolean wounded) {
        return holdingPost && enemyInRange && !venting && !wounded;
    }

    /** At an enemy in a post, far enough off that the blast spares the thrower, since it would hurt them too. */
    static boolean throwsCharge(boolean targetInPost, double distance, double blastRadius) {
        return targetInPost && distance > blastRadius + SAFE_MARGIN && distance <= LONGEST_THROW;
    }

    /** The direction that carries a throw of {@code speed} blocks per tick across {@code toTarget}'s level distance. */
    static Vector lob(Vector toTarget, double speed) {
        var level = toTarget.clone().setY(0);
        double distance = level.length();
        if (distance == 0) {
            return new Vector(0, 1, 0);
        }
        double angle = Math.asin(Math.min(1, distance * GRAVITY / (speed * speed))) / 2;
        return level.multiply(Math.cos(angle) / distance).setY(Math.sin(angle));
    }
}
