package me.blockcraft.sternenfront.hero;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kills since the last death; reaching {@code reward} kills in one life earns the streak's reward once. */
public final class KillStreaks {

    private final int reward;
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public KillStreaks(int reward) {
        this.reward = reward;
    }

    /** Counts a kill; true exactly when it completes the rewarded streak. */
    public boolean kill(UUID killer) {
        return streaks.merge(killer, 1, Integer::sum) == reward;
    }

    public void death(UUID victim) {
        streaks.remove(victim);
    }

    public void clear() {
        streaks.clear();
    }
}
