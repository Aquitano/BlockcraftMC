package me.blockcraft.common.calendar;

import me.blockcraft.common.config.StrictConfig;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The days the network celebrates, counted in its own time zone so midnight falls when it does for its players.
 * Easter moves every year and is computed; Halloween, winter and the network's birthday come from {@code calendar}
 * in the config, and Advent, Christmas, New Year's Eve and 4 May, the day of the stars, are what they always are. OG, the rounds on the maps of the
 * old server, has no date. Staff can force a set of occasions network-wide with {@code /event}; while one is forced,
 * it is what today celebrates.
 */
public final class NetworkCalendar {

    public enum Occasion { EASTER, HALLOWEEN, WINTER, ADVENT, CHRISTMAS, NEW_YEARS_EVE, BIRTHDAY, OG, MAY_THE_FOURTH }

    /** From one day to another, both included; a window whose end comes before its start runs across New Year. */
    public record Window(MonthDay from, MonthDay to) {

        public boolean includes(MonthDay day) {
            return from.isAfter(to)
                    ? !day.isBefore(from) || !day.isAfter(to)
                    : !day.isBefore(from) && !day.isAfter(to);
        }
    }

    private static final int LAST_ADVENT_DOOR = 24;
    private static final Window CHRISTMAS = new Window(MonthDay.of(Month.DECEMBER, 24), MonthDay.of(Month.DECEMBER, 26));
    private static final MonthDay MAY_THE_FOURTH = MonthDay.of(Month.MAY, 4);

    private final Clock clock;
    private final int easterDaysBefore;
    private final int easterDaysAfter;
    private final Window halloween;
    private final Window winter;
    private final Optional<LocalDate> founded;
    private volatile Optional<Set<Occasion>> forced = Optional.empty();

    public NetworkCalendar(Clock clock, int easterDaysBefore, int easterDaysAfter, Window halloween, Window winter,
                           Optional<LocalDate> founded) {
        this.clock = clock;
        this.easterDaysBefore = easterDaysBefore;
        this.easterDaysAfter = easterDaysAfter;
        this.halloween = halloween;
        this.winter = winter;
        this.founded = founded;
    }

    /**
     * {@code calendar} of a config.yml: {@code zone}, {@code easter.days-before/-after}, {@code halloween.from/to},
     * {@code winter.from/to}, {@code founded}. SnakeYAML hands an unquoted founded over as a {@link Date} at UTC midnight.
     */
    public static NetworkCalendar from(StrictConfig section) {
        return new NetworkCalendar(Clock.system(ZoneId.of(section.text("zone"))),
                Math.max(0, section.integer("easter.days-before")), Math.max(0, section.integer("easter.days-after")),
                window(section.section("halloween")), window(section.section("winter")), founded(section));
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
        return occasions().contains(occasion);
    }

    /** What today celebrates: the forced occasions while there are any, else what the date says. */
    public Set<Occasion> occasions() {
        return forced.orElseGet(() -> occasions(today()));
    }

    public Optional<Set<Occasion>> forced() {
        return forced;
    }

    /** Replaces what the date says with exactly {@code occasions}; empty lets the date decide again. */
    public void force(Optional<Set<Occasion>> occasions) {
        forced = occasions.map(chosen -> {
            var copy = EnumSet.noneOf(Occasion.class);
            copy.addAll(chosen);
            return Collections.unmodifiableSet(copy);
        });
    }

    /** What the date alone celebrates, whatever staff have forced. */
    public Set<Occasion> occasions(LocalDate day) {
        var occasions = EnumSet.noneOf(Occasion.class);
        var easter = easterSunday(day.getYear());
        if (!day.isBefore(easter.minusDays(easterDaysBefore)) && !day.isAfter(easter.plusDays(easterDaysAfter))) {
            occasions.add(Occasion.EASTER);
        }
        var monthDay = MonthDay.from(day);
        if (halloween.includes(monthDay)) {
            occasions.add(Occasion.HALLOWEEN);
        }
        if (winter.includes(monthDay)) {
            occasions.add(Occasion.WINTER);
        }
        if (adventDoor(day).isPresent()) {
            occasions.add(Occasion.ADVENT);
        }
        if (CHRISTMAS.includes(monthDay)) {
            occasions.add(Occasion.CHRISTMAS);
        }
        if (monthDay.equals(MonthDay.of(Month.DECEMBER, 31))) {
            occasions.add(Occasion.NEW_YEARS_EVE);
        }
        if (monthDay.equals(MAY_THE_FOURTH)) {
            occasions.add(Occasion.MAY_THE_FOURTH);
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

    private static Window window(StrictConfig section) {
        return new Window(MonthDay.parse("--" + section.text("from")), MonthDay.parse("--" + section.text("to")));
    }

    private static Optional<LocalDate> founded(StrictConfig section) {
        if (section.values().get("founded") instanceof Date day) {
            return Optional.of(day.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        }
        var text = section.text("founded");
        return text.isBlank() ? Optional.empty() : Optional.of(LocalDate.parse(text));
    }
}
