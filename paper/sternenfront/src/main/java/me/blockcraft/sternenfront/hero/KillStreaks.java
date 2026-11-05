package me.blockcraft.sternenfront.hero;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kills since the last death; reaching {@code reward} kills in one life earns the streak's reward once. It takes
 * {@code botsPerKill} bots to count as one kill.
 */
public final class KillStreaks {

    private final int botsPerKill;
    private final int target;
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public KillStreaks(int reward, int botsPerKill) {
        this.botsPerKill = botsPerKill;
        target = reward * botsPerKill;
    }

    /** Counts a kill; true exactly when it completes the rewarded streak. */
    public boolean kill(UUID killer) {
        return add(killer, botsPerKill);
    }

    public boolean botKill(UUID killer) {
        return add(killer, 1);
    }

    private boolean add(UUID killer, int weight) {
        int streak = streaks.merge(killer, weight, Integer::sum);
        return streak >= target && streak - weight < target;
    }

    public void death(UUID victim) {
        streaks.remove(victim);
    }

    public void clear() {
        streaks.clear();
    }
}
