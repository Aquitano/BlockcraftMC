package me.blockcraft.system.moderation;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/** One-token durations, "16d", "12h" or "3601s", as the /strafe catalogue, /tempban and /tempmute write them. */
public final class Durations {

    private static final Pattern SPEC = Pattern.compile("(\\d{1,9})\\s*([a-zA-Z]+)");

    private Durations() {
    }

    /** A catalogue entry: a timed spec or "permanent" (empty). Throws on anything else. */
    public static Optional<Duration> parse(String spec) {
        if (spec.equalsIgnoreCase("permanent")) {
            return Optional.empty();
        }
        return Optional.of(timed(spec).orElseThrow(() -> new IllegalArgumentException("Invalid duration: " + spec)));
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
