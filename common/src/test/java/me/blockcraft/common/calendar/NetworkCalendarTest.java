package me.blockcraft.common.calendar;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import org.junit.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class NetworkCalendarTest {

    private static final NetworkCalendar CALENDAR = new NetworkCalendar(Clock.systemUTC(), 2, 7,
            MonthDay.of(10, 25), MonthDay.of(10, 31), Optional.of(LocalDate.of(2018, 6, 3)));

    @Test
    public void easterSundayMovesWithTheMoon() {
        assertEquals(LocalDate.of(2025, 4, 20), NetworkCalendar.easterSunday(2025));
        assertEquals(LocalDate.of(2026, 4, 5), NetworkCalendar.easterSunday(2026));
        assertEquals(LocalDate.of(2027, 3, 28), NetworkCalendar.easterSunday(2027));
        assertEquals(LocalDate.of(2038, 4, 25), NetworkCalendar.easterSunday(2038));
    }

    @Test
    public void eachDayKnowsWhatItCelebrates() {
        assertEquals(Set.of(Occasion.EASTER), CALENDAR.occasions(LocalDate.of(2026, 4, 3)));
        assertEquals(Set.of(), CALENDAR.occasions(LocalDate.of(2026, 4, 13)));
        assertEquals(Set.of(Occasion.HALLOWEEN), CALENDAR.occasions(LocalDate.of(2026, 10, 31)));
        assertEquals(Set.of(Occasion.ADVENT), CALENDAR.occasions(LocalDate.of(2026, 12, 24)));
        assertEquals(Set.of(Occasion.NEW_YEARS_EVE), CALENDAR.occasions(LocalDate.of(2026, 12, 31)));
        assertEquals(Set.of(Occasion.BIRTHDAY), CALENDAR.occasions(LocalDate.of(2026, 6, 3)));
    }

    @Test
    public void theBirthdayCountsYearsAndTheFirstDayIsNone() {
        assertEquals(OptionalInt.of(8), CALENDAR.age(LocalDate.of(2026, 6, 3)));
        assertEquals(OptionalInt.empty(), CALENDAR.age(LocalDate.of(2018, 6, 3)));
        assertEquals(OptionalInt.of(24), CALENDAR.adventDoor(LocalDate.of(2026, 12, 24)));
        assertEquals(OptionalInt.empty(), CALENDAR.adventDoor(LocalDate.of(2026, 12, 25)));
    }
}
