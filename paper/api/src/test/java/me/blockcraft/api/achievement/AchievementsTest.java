package me.blockcraft.api.achievement;

import me.blockcraft.api.game.Stats;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class AchievementsTest {

    private static final List<Achievement> CATALOG = Achievements.parse(Map.of(
            "quicksg", Map.of(
                    "first-win", Map.of("counts", "wins", "threshold", 1, "xp", 25),
                    "kills-10", Map.of("counts", "kills", "threshold", 10, "xp", 25),
                    "kills-100", Map.of("counts", "kills", "threshold", 100, "xp", 100)),
            "duell", Map.of("kills-10", Map.of("counts", "kills", "threshold", 10, "xp", 25)),
            "network", Map.of("level-5", Map.of("counts", "level", "threshold", 5, "xp", 50))));

    @Test
    public void roundTotalsUnlockWhatTheyReachAndNothingOwned() {
        var totals = new Stats(12, 3, 1, 4, 0);

        var reached = Achievements.reached(CATALOG, "quicksg", totals, 1, Set.of("achievement.quicksg.first-win"));

        assertEquals(List.of("quicksg.kills-10"), reached.stream().map(Achievement::key).toList());
    }

    @Test
    public void levelEntriesFollowTheNetworkLevelAlone() {
        assertEquals(List.of(), Achievements.reached(CATALOG, Achievement.NETWORK, Stats.EMPTY, 4, Set.of()));
        assertEquals(List.of("network.level-5"),
                Achievements.reached(CATALOG, Achievement.NETWORK, Stats.EMPTY, 5, Set.of()).stream().map(Achievement::key).toList());
    }
}
