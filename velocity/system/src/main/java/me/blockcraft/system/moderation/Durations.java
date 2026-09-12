package me.blockcraft.system.moderation;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Durations {

    private static final Pattern SPEC = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private Durations() {
    }

    /** "16d", "12h", "3601s" or "permanent" (empty). Throws on anything else. */
    public static Optional<Duration> parse(String spec) {
        if (spec.equalsIgnoreCase("permanent")) {
            return Optional.empty();
        }
        var matcher = SPEC.matcher(spec.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration: " + spec);
        }
        return Optional.of(of(Long.parseLong(matcher.group(1)), matcher.group(2))
                .orElseThrow(() -> new IllegalArgumentException("Invalid duration unit: " + spec)));
    }

    /** Units accepted by /tempban and /tempmute; empty for an unknown unit. */
    public static Optional<Duration> of(long amount, String unit) {
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
