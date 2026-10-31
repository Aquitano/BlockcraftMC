package me.blockcraft.system.friends;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FriendTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    public void ageAndAnniversaryUseTheNetworksLocalDay() {
        var friend = new Friend(UUID.randomUUID(), "Mia", Instant.parse("2025-10-12T22:30:00Z"));

        assertEquals(0, friend.days(LocalDate.of(2025, 10, 13), BERLIN));
        assertEquals(412, friend.days(LocalDate.of(2026, 11, 29), BERLIN));
        assertEquals(OptionalInt.empty(), friend.anniversary(LocalDate.of(2025, 10, 13), BERLIN));
        assertEquals(OptionalInt.empty(), friend.anniversary(LocalDate.of(2026, 10, 12), BERLIN));
        assertEquals(OptionalInt.of(1), friend.anniversary(LocalDate.of(2026, 10, 13), BERLIN));
    }

    @Test
    public void leapDayFriendshipsCelebrateOnFebruary28InOtherYears() {
        var friend = new Friend(UUID.randomUUID(), "Mia", Instant.parse("2024-02-29T12:00:00Z"));

        assertEquals(OptionalInt.of(1), friend.anniversary(LocalDate.of(2025, 2, 28), BERLIN));
        assertEquals(OptionalInt.empty(), friend.anniversary(LocalDate.of(2025, 3, 1), BERLIN));
        assertEquals(OptionalInt.of(4), friend.anniversary(LocalDate.of(2028, 2, 29), BERLIN));
    }
}
