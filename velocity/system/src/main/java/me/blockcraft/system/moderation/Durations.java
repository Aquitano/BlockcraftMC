package me.blockcraft.system.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/** One-token durations, "16d", "12h" or "3601s", as the /strafe catalogue, /tempban and /tempmute write them. */
public final class Durations {

    private static final Duration FIVE_YEARS = Duration.ofDays(5 * 365);
    /** The end is stored as a TIMESTAMP, which stops here; MariaDB turns anything past it into permanent or expired. */
    private static final Instant TIMESTAMP_END = Instant.parse("2038-01-19T03:14:07Z");

    private static final Pattern SPEC = Pattern.compile("(\\d{1,9})\\s*([a-zA-Z]+)");

    private Durations() {
    }

    /** A catalogue entry: a timed spec up to {@link #longest()} or "permanent" (empty). Throws on anything else. */
    public static Optional<Duration> parse(String spec) {
        if (spec.equalsIgnoreCase("permanent")) {
            return Optional.empty();
        }
        return Optional.of(timed(spec).filter(Durations::fits).orElseThrow(() -> new IllegalArgumentException("Invalid duration: " + spec)));
    }

    public static boolean fits(Duration duration) {
        return duration.compareTo(longest()) <= 0;
    }

    /** Five years, or what is left until the TIMESTAMP range ends once that comes closer. */
    public static Duration longest() {
        return longest(Instant.now());
    }

    static Duration longest(Instant now) {
        var untilEnd = Duration.between(now, TIMESTAMP_END);
        return untilEnd.compareTo(FIVE_YEARS) < 0 ? untilEnd : FIVE_YEARS;
    }

    /** A timed spec as typed by staff; empty when the token is not one, or would end at once. */
    public static Optional<Duration> timed(String spec) {
        var matcher = SPEC.matcher(spec.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return of(Long.parseLong(matcher.group(1)), matcher.group(2)).filter(duration -> !duration.isZero());
    }

    private static Optional<Duration> of(long amount, String unit) {
        return switch (unit.toLowerCase()) {
            case "s", "sec", "secs", "second", "seconds" -> Optional.of(Duration.ofSeconds(amount));
            case "m", "min", "mins", "minute", "minutes" -> Optional.of(Duration.ofMinutes(amount));
            case "h", "hour", "hours" -> Optional.of(Duration.ofHours(amount));
            case "d", "day", "days" -> Optional.of(Duration.ofDays(amount));
            case "w", "week", "weeks" -> Optional.of(Duration.ofDays(amount * 7));
            case "mon", "month", "months" -> Optional.of(Duration.ofDays(amount * 30));
            case "y", "year", "years" -> Optional.of(Duration.ofDays(amount * 360));
            default -> Optional.empty();
        };
    }
}
