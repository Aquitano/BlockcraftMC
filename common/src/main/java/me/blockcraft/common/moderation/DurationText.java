package me.blockcraft.common.moderation;

import me.blockcraft.common.message.Messages;

import java.time.Duration;

/**
 * Renders a duration the way the old proxy did ("2 Tage 3 Stunden 0 Minuten 0 Sekunden"), with the unit
 * words taken from {@code duration.*} in messages.yml.
 */
public final class DurationText {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 360 * DAY;

    private final Messages messages;

    public DurationText(Messages messages) {
        this.messages = messages;
    }

    public String format(Duration duration) {
        return years(Math.max(0, duration.toSeconds()));
    }

    private String years(long seconds) {
        return seconds < YEAR ? months(seconds) : unit(seconds / YEAR, "year") + " " + months(seconds % YEAR);
    }

    private String months(long seconds) {
        return seconds < MONTH ? weeks(seconds) : unit(seconds / MONTH, "month") + " " + weeks(seconds % MONTH);
    }

    private String weeks(long seconds) {
        return seconds < WEEK ? days(seconds) : unit(seconds / WEEK, "week") + " " + days(seconds % WEEK);
    }

    private String days(long seconds) {
        return seconds < DAY ? hours(seconds) : unit(seconds / DAY, "day") + " " + hours(seconds % DAY);
    }

    private String hours(long seconds) {
        return seconds < HOUR ? minutes(seconds) : unit(seconds / HOUR, "hour") + " " + minutes(seconds % HOUR);
    }

    private String minutes(long seconds) {
        return seconds < MINUTE ? unit(seconds, "second") : unit(seconds / MINUTE, "minute") + " " + unit(seconds % MINUTE, "second");
    }

    private String unit(long amount, String key) {
        return amount + " " + messages.raw("duration." + key + (amount == 1 ? "" : "s"));
    }
}
