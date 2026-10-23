package me.blockcraft.system.welcome;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.db.Database;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players online on an anniversary of their first join, counted in the calendar's zone. The login notes them,
 * so the online notice to their friends can say it without reading {@code bc_players} again.
 */
public final class Anniversaries {

    private final NetworkCalendar calendar;
    private final Database database;
    private final Map<UUID, LocalDate> firstDays = new ConcurrentHashMap<>();

    public Anniversaries(NetworkCalendar calendar, Database database) {
        this.calendar = calendar;
        this.database = database;
    }

    /** Notes the player when today is an anniversary of their first join. */
    public void arrive(UUID player, Instant firstSeen) {
        var firstDay = LocalDate.ofInstant(firstSeen, calendar.zone());
        firstDays.put(player, firstDay);
        years(firstDay, calendar.today()).ifPresent(count -> {
            database.update("INSERT IGNORE INTO bc_unlocks (player_uuid, item) VALUES (?, 'gadget.birthday-cake')", player.toString());
        });
    }

    /** How many years ago today the player first joined, while it is their anniversary. */
    public OptionalInt years(UUID player) {
        var firstDay = firstDays.get(player);
        return firstDay == null ? OptionalInt.empty() : years(firstDay, calendar.today());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        firstDays.remove(event.getPlayer().getUniqueId());
    }

    /** A first join on February 29 comes round on February 28 in the years without one. */
    static OptionalInt years(LocalDate firstDay, LocalDate today) {
        int years = today.getYear() - firstDay.getYear();
        return years > 0 && firstDay.plusYears(years).equals(today) ? OptionalInt.of(years) : OptionalInt.empty();
    }
}
