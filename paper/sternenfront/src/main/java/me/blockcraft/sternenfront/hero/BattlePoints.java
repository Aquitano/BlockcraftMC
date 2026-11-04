package me.blockcraft.sternenfront.hero;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each player's battle points: kills and captures earn them, fighting as a hero spends them. */
public final class BattlePoints {

    private final Map<UUID, Integer> points = new HashMap<>();

    public int of(UUID player) {
        return points.getOrDefault(player, 0);
    }

    public void earn(UUID player, int amount) {
        if (amount > 0) {
            points.merge(player, amount, Integer::sum);
        }
    }

    /** Takes {@code cost} points if the player has them; false and nothing taken otherwise. */
    public boolean spend(UUID player, int cost) {
        int held = of(player);
        if (held < cost) {
            return false;
        }
        points.put(player, held - cost);
        return true;
    }

    public void forget(UUID player) {
        points.remove(player);
    }

    public void clear() {
        points.clear();
    }
}
