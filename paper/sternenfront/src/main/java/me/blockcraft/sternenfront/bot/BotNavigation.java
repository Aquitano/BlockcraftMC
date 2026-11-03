package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

import java.util.List;

final class BotNavigation {

    private BotNavigation() { }

    static int enter(Vector here, Vector objective, List<Vector> approach) {
        int chosen = approach.size();
        double nearest = Double.MAX_VALUE;
        double remaining = here.distanceSquared(objective);
        for (int index = 0; index < approach.size(); index++) {
            var point = approach.get(index);
            double distance = here.distanceSquared(point);
            if (point.distanceSquared(objective) <= remaining + 1 && distance < nearest) {
                chosen = index;
                nearest = distance;
            }
        }
        return chosen;
    }
}
