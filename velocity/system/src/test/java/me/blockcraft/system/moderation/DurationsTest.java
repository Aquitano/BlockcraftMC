package me.blockcraft.system.moderation;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class DurationsTest {

    @Test
    public void aTimedPunishmentLastsFiveYearsAtMost() {
        assertEquals(Duration.ofDays(5 * 365), Durations.longest(Instant.parse("2026-10-20T00:00:00Z")));
    }

    @Test
    public void theLimitShrinksOnceTheTimestampRangeEndsWithinFiveYears() {
        assertEquals(Duration.ofDays(30), Durations.longest(Instant.parse("2037-12-20T03:14:07Z")));
    }
}
