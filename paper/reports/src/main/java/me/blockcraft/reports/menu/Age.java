package me.blockcraft.reports.menu;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

import java.time.Duration;
import java.time.Instant;

/** Only the largest unit that still fits, so a report's age stays one short line in a lore. */
final class Age {

    private static final long MINUTES_PER_HOUR = 60;
    private static final long HOURS_PER_DAY = 24;

    private Age() {
    }

    static Component since(Messages messages, Instant moment) {
        long minutes = Math.max(0, Duration.between(moment, Instant.now()).toMinutes());
        if (minutes < 1) {
            return messages.get("age.now");
        }
        if (minutes < MINUTES_PER_HOUR) {
            return unit(messages, "age.minutes", minutes);
        }
        long hours = minutes / MINUTES_PER_HOUR;
        return hours < HOURS_PER_DAY ? unit(messages, "age.hours", hours) : unit(messages, "age.days", hours / HOURS_PER_DAY);
    }

    private static Component unit(Messages messages, String key, long amount) {
        return messages.get(key, Formatter.number("amount", amount));
    }
}
