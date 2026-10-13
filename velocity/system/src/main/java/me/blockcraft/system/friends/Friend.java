package me.blockcraft.system.friends;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.OptionalInt;
import java.util.UUID;

/** The other side of a {@code bc_friends} row: an accepted friend or the sender of an open request. */
public record Friend(UUID uuid, String name, Instant createdAt) {

    public long days(LocalDate today, ZoneId zone) {
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.ofInstant(createdAt, zone), today));
    }

    public OptionalInt anniversary(LocalDate today, ZoneId zone) {
        var firstDay = LocalDate.ofInstant(createdAt, zone);
        int years = today.getYear() - firstDay.getYear();
        return years > 0 && firstDay.plusYears(years).equals(today) ? OptionalInt.of(years) : OptionalInt.empty();
    }
}
