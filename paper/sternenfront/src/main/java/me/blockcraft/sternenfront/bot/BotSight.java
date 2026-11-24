package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

final class BotSight {

    private BotSight() { }

    static boolean sees(Vector facing, Vector toward, double angle) {
        var ahead = facing.clone().setY(0);
        var across = toward.clone().setY(0);
        return across.lengthSquared() < 0.01 || ahead.lengthSquared() > 0.01
                && ahead.normalize().dot(across.normalize()) >= Math.cos(Math.toRadians(angle / 2));
    }
}
