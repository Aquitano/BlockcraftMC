package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

/** When a bot spends its class's abilities, apart from the world it reads. */
final class BotTactics {

    private static final double SAFE_MARGIN = 1;
    private static final double LONGEST_THROW = 18;
    private static final double GRAVITY = 0.04;
    private static final double CLOSEST_WALL = 6;
    private static final double FARTHEST_WALL = 24;

    private BotTactics() {
    }

    /** Rooting itself only pays where it stands anyway, with something to shoot and a gun and body fit to stay. */
    static boolean sieges(boolean holdingPost, boolean enemyInRange, boolean venting, boolean wounded) {
        return holdingPost && enemyInRange && !venting && !wounded;
    }

    /** A guard stands on its attacked post's point unless it is wounded or venting and has cover to wait behind. */
    static boolean contests(boolean covered, boolean wounded, boolean venting) {
        return !covered || !wounded && !venting;
    }

    /**
     * At an enemy dug in, in a post or out of sight behind cover, far enough off that the blast spares the thrower,
     * since it would hurt them too.
     */
    static boolean throwsAt(boolean dugIn, double distance, double blastRadius) {
        return dugIn && distance > blastRadius + SAFE_MARGIN && distance <= LONGEST_THROW;
    }

    /** Where the bot holds its ground under fire with nothing to hide behind, against an enemy too close to ignore. */
    static boolean raisesWall(boolean holdingGround, boolean covered, double threatDistance) {
        return holdingGround && !covered && threatDistance >= CLOSEST_WALL && threatDistance <= FARTHEST_WALL;
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
