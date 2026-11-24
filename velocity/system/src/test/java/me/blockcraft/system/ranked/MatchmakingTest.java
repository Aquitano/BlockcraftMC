package me.blockcraft.system.ranked;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MatchmakingTest {

    private static final RankedQueue.Settings SETTINGS = new RankedQueue.Settings("duell", 100, 10, 400, Duration.ofMinutes(3));
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    public void closeNeighboursMeetAndTheGapWidensWithTheWait() {
        var low = seeker(1000, 0);
        var near = seeker(1080, 0);
        var far = seeker(1400, 0);

        assertEquals(List.of(new Matchmaking.Pair(low, near)), Matchmaking.pairs(List.of(far, near, low), NOW, SETTINGS));
        assertEquals(List.of(), Matchmaking.pairs(List.of(low, far), NOW, SETTINGS));

        var patientLow = seeker(1000, 30);
        var patientFar = seeker(1400, 30);
        assertEquals(List.of(new Matchmaking.Pair(patientLow, patientFar)),
                Matchmaking.pairs(List.of(patientLow, patientFar), NOW, SETTINGS));
    }

    private static Matchmaking.Seeker seeker(int rating, int waitedSeconds) {
        return new Matchmaking.Seeker(UUID.randomUUID(), rating, NOW.minusSeconds(waitedSeconds));
    }
}
