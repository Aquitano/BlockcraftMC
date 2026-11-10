package me.blockcraft.api.nick;

import me.blockcraft.api.rank.NickRanks;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class NickStatsTest {

    private static final NickStats STATS = new NickStats(new NickRanks(), NickStats.Ranges.from(Map.of(
            "rounds", Map.of("min", 50, "max", 100),
            "win-rate", Map.of("min", 0.1, "max", 0.2),
            "rating", Map.of("min", 1000, "max", 1100))));

    @Test
    public void aNickAlwaysWearsTheSamePlausibleNumbers() {
        var first = STATS.disguise("Steve");
        var again = STATS.disguise("Steve");

        assertEquals(first, again);
        assertNotEquals(first, STATS.disguise("Alex"));
        var stats = first.stats();
        int rounds = stats.wins() + stats.losses();
        assertTrue(rounds >= 50 && rounds <= 100);
        assertTrue(stats.wins() >= rounds * 0.1 - 1 && stats.wins() <= rounds * 0.2 + 1);
        assertTrue(stats.deaths() >= stats.losses());
        assertTrue(first.rating().rating() >= 1000 && first.rating().rating() <= 1100);
    }
}
