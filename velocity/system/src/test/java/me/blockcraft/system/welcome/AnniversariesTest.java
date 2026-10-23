package me.blockcraft.system.welcome;

import org.junit.Test;

import java.time.LocalDate;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;

public class AnniversariesTest {

    @Test
    public void onlyTheSameDayInALaterYearIsAnAnniversary() {
        var firstDay = LocalDate.of(2023, 10, 12);

        assertEquals(OptionalInt.of(3), Anniversaries.years(firstDay, LocalDate.of(2026, 10, 12)));
        assertEquals(OptionalInt.empty(), Anniversaries.years(firstDay, firstDay));
        assertEquals(OptionalInt.empty(), Anniversaries.years(firstDay, LocalDate.of(2026, 10, 13)));
    }

    @Test
    public void aLeapDayIsCelebratedOnTheLastDayOfFebruary() {
        var leapDay = LocalDate.of(2024, 2, 29);

        assertEquals(OptionalInt.of(1), Anniversaries.years(leapDay, LocalDate.of(2025, 2, 28)));
        assertEquals(OptionalInt.empty(), Anniversaries.years(leapDay, LocalDate.of(2025, 3, 1)));
        assertEquals(OptionalInt.of(4), Anniversaries.years(leapDay, LocalDate.of(2028, 2, 29)));
    }
}
