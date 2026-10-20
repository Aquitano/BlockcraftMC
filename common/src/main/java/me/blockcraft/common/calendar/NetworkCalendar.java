package me.blockcraft.common.calendar;

import me.blockcraft.common.config.YamlFile;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The days the network celebrates, counted in its own time zone so midnight falls when it does for its players.
 * Easter moves every year and is computed; Halloween and the network's birthday come from {@code calendar} in the
 * config, and Advent and New Year's Eve are what they always are.
 */
public final class NetworkCalendar {

    public enum Occasion { EASTER, HALLOWEEN, ADVENT, NEW_YEARS_EVE, BIRTHDAY }

    private static final int LAST_ADVENT_DOOR = 24;

    private final Clock clock;
    private final int easterDaysBefore;
    private final int easterDaysAfter;
    private final MonthDay halloweenFrom;
    private final MonthDay halloweenTo;
    private final Optional<LocalDate> founded;

    public NetworkCalendar(Clock clock, int easterDaysBefore, int easterDaysAfter, MonthDay halloweenFrom, MonthDay halloweenTo,
                           Optional<LocalDate> founded) {
        this.clock = clock;
        this.easterDaysBefore = easterDaysBefore;
        this.easterDaysAfter = easterDaysAfter;
        this.halloweenFrom = halloweenFrom;
        this.halloweenTo = halloweenTo;
        this.founded = founded;
    }

    /**
     * {@code calendar} of a config.yml: {@code zone}, {@code easter.days-before/-after}, {@code halloween.from/to},
     * {@code founded}. SnakeYAML hands an unquoted founded over as a {@link Date} at UTC midnight.
     */
    public static NetworkCalendar fromYaml(Map<String, Object> section) {
        var easter = YamlFile.section(section, "easter");
        var halloween = YamlFile.section(section, "halloween");
        return new NetworkCalendar(Clock.system(zone(section.get("zone"))),
                number(easter, "days-before", 2), number(easter, "days-after", 7),
                monthDay(halloween.get("from"), MonthDay.of(Month.OCTOBER, 25)),
                monthDay(halloween.get("to"), MonthDay.of(Month.OCTOBER, 31)),
                date(section.get("founded")));
    }

    public ZoneId zone() {
        return clock.getZone();
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public boolean is(Occasion occasion) {
        return occasions(today()).contains(occasion);
    }

    public Set<Occasion> occasions(LocalDate day) {
        var occasions = EnumSet.noneOf(Occasion.class);
        var easter = easterSunday(day.getYear());
        if (!day.isBefore(easter.minusDays(easterDaysBefore)) && !day.isAfter(easter.plusDays(easterDaysAfter))) {
            occasions.add(Occasion.EASTER);
        }
        var monthDay = MonthDay.from(day);
        if (!monthDay.isBefore(halloweenFrom) && !monthDay.isAfter(halloweenTo)) {
            occasions.add(Occasion.HALLOWEEN);
        }
        if (adventDoor(day).isPresent()) {
            occasions.add(Occasion.ADVENT);
        }
        if (monthDay.equals(MonthDay.of(Month.DECEMBER, 31))) {
            occasions.add(Occasion.NEW_YEARS_EVE);
        }
        if (age(day).isPresent()) {
            occasions.add(Occasion.BIRTHDAY);
        }
        return occasions;
    }

    /** The door of the Advent calendar that opens on the day: its day of December up to Christmas Eve. */
    public OptionalInt adventDoor(LocalDate day) {
        return day.getMonth() == Month.DECEMBER && day.getDayOfMonth() <= LAST_ADVENT_DOOR
                ? OptionalInt.of(day.getDayOfMonth()) : OptionalInt.empty();
    }

    /** How many years old the network turns on the day, when the day is its birthday. */
    public OptionalInt age(LocalDate day) {
        return founded.filter(opened -> MonthDay.from(opened).equals(MonthDay.from(day)) && day.getYear() > opened.getYear())
                .map(opened -> OptionalInt.of(day.getYear() - opened.getYear()))
                .orElse(OptionalInt.empty());
    }

    /** Easter Sunday of the Gregorian calendar, by the anonymous Gregorian algorithm. */
    public static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    private static ZoneId zone(Object value) {
        try {
            return value instanceof String id && !id.isBlank() ? ZoneId.of(id) : ZoneId.of("Europe/Berlin");
        } catch (DateTimeException invalid) {
            return ZoneId.of("Europe/Berlin");
        }
    }

    private static MonthDay monthDay(Object value, MonthDay fallback) {
        try {
            return value instanceof String text ? MonthDay.parse("--" + text) : fallback;
        } catch (DateTimeParseException invalid) {
            return fallback;
        }
    }

    private static Optional<LocalDate> date(Object value) {
        if (value instanceof Date day) {
            return Optional.of(day.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        }
        try {
            return value instanceof String text && !text.isBlank() ? Optional.of(LocalDate.parse(text)) : Optional.empty();
        } catch (DateTimeParseException invalid) {
            return Optional.empty();
        }
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number value ? Math.max(0, value.intValue()) : fallback;
    }
}
