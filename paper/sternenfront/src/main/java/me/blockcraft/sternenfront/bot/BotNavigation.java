package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;

import java.util.List;

final class BotNavigation {

    private int route;
    private int attempts;

    void start(int preferred, int count) {
        route = Math.floorMod(preferred, count);
        attempts = 1;
    }

    int route() {
        return route;
    }

    boolean retry(int count) {
        if (attempts >= Math.min(2, count)) {
            return false;
        }
        route = (route + 1) % count;
        attempts++;
        return true;
    }

    static int enter(Vector here, Vector objective, List<Vector> approach) {
        if (approach.isEmpty()) {
            return 0;
        }
        int chosen = 0;
        double nearest = here.distanceSquared(approach.getFirst());
        for (int index = 1; index <= approach.size(); index++) {
            var from = approach.get(index - 1);
            var to = index == approach.size() ? objective : approach.get(index);
            var direction = to.clone().subtract(from);
            if (direction.lengthSquared() < 0.01) {
                continue;
            }
            double fraction = Math.clamp(here.clone().subtract(from).dot(direction) / direction.lengthSquared(), 0, 1);
            double distance = here.distanceSquared(from.clone().add(direction.multiply(fraction)));
            if (index == approach.size() && distance > 9) {
                continue;
            }
            if (fraction > 0 && distance < nearest) {
                chosen = index;
                nearest = distance;
            }
        }
        return chosen;
    }
}
