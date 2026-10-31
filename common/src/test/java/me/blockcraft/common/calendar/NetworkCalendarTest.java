package me.blockcraft.common.calendar;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.calendar.NetworkCalendar.Window;
import me.blockcraft.common.config.YamlFile;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class NetworkCalendarTest {

    private static final NetworkCalendar CALENDAR = new NetworkCalendar(Clock.systemUTC(), 2, 7,
            new Window(MonthDay.of(10, 17), MonthDay.of(10, 31)), new Window(MonthDay.of(12, 1), MonthDay.of(12, 31)),
            Optional.of(LocalDate.of(2018, 6, 3)));

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
        assertEquals(Set.of(), CALENDAR.occasions(LocalDate.of(2026, 10, 16)));
        assertEquals(Set.of(Occasion.HALLOWEEN), CALENDAR.occasions(LocalDate.of(2026, 10, 17)));
        assertEquals(Set.of(Occasion.HALLOWEEN), CALENDAR.occasions(LocalDate.of(2026, 10, 31)));
        assertEquals(Set.of(Occasion.WINTER, Occasion.ADVENT), CALENDAR.occasions(LocalDate.of(2026, 12, 1)));
        assertEquals(Set.of(Occasion.WINTER, Occasion.ADVENT, Occasion.CHRISTMAS), CALENDAR.occasions(LocalDate.of(2026, 12, 24)));
        assertEquals(Set.of(Occasion.WINTER, Occasion.CHRISTMAS), CALENDAR.occasions(LocalDate.of(2026, 12, 26)));
        assertEquals(Set.of(Occasion.WINTER, Occasion.NEW_YEARS_EVE), CALENDAR.occasions(LocalDate.of(2026, 12, 31)));
        assertEquals(Set.of(Occasion.BIRTHDAY), CALENDAR.occasions(LocalDate.of(2026, 6, 3)));
    }

    @Test
    public void forcedOccasionsReplaceTheDateUntilTheDateDecidesAgain() {
        var october = new NetworkCalendar(Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC), 2, 7,
                new Window(MonthDay.of(10, 17), MonthDay.of(10, 31)), new Window(MonthDay.of(12, 1), MonthDay.of(12, 31)),
                Optional.empty());

        october.force(Optional.of(Set.of(Occasion.CHRISTMAS, Occasion.WINTER)));
        assertEquals(Set.of(Occasion.CHRISTMAS, Occasion.WINTER), october.occasions());
        assertEquals(false, october.is(Occasion.HALLOWEEN));

        october.force(Optional.of(Set.of()));
        assertEquals(Set.of(), october.occasions());

        october.force(Optional.empty());
        assertEquals(Set.of(Occasion.HALLOWEEN), october.occasions());
    }

    @Test
    public void aWindowEndingBeforeItStartsRunsAcrossNewYear() {
        var winter = new Window(MonthDay.of(12, 1), MonthDay.of(1, 6));

        assertEquals(true, winter.includes(MonthDay.of(12, 31)));
        assertEquals(true, winter.includes(MonthDay.of(1, 6)));
        assertEquals(false, winter.includes(MonthDay.of(1, 7)));
        assertEquals(false, winter.includes(MonthDay.of(11, 30)));
    }

    @Test
    public void theBirthdayCountsYearsAndTheFirstDayIsNone() {
        assertEquals(OptionalInt.of(8), CALENDAR.age(LocalDate.of(2026, 6, 3)));
        assertEquals(OptionalInt.empty(), CALENDAR.age(LocalDate.of(2018, 6, 3)));
        assertEquals(OptionalInt.of(24), CALENDAR.adventDoor(LocalDate.of(2026, 12, 24)));
        assertEquals(OptionalInt.empty(), CALENDAR.adventDoor(LocalDate.of(2026, 12, 25)));
    }

    @Test
    public void foundedCountsWhetherOrNotYamlQuotesIt() {
        for (String founded : new String[]{"2018-06-03", "\"2018-06-03\""}) {
            var calendar = NetworkCalendar.fromYaml(YamlFile.load(
                    new ByteArrayInputStream(("founded: " + founded).getBytes(StandardCharsets.UTF_8))));

            assertEquals(OptionalInt.of(8), calendar.age(LocalDate.of(2026, 6, 3)));
        }
    }
}
