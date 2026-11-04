package me.blockcraft.api.game;

import me.blockcraft.common.db.Database;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** How often each account has killed each other one, in any game, in {@code bc_rivalries}. */
final class RivalryRepository {

    record Rivalry(UUID killer, UUID victim, int kills) {
    }

    private final Database db;

    RivalryRepository(Database db) {
        this.db = db;
    }

    void add(UUID killer, UUID victim) {
        db.update("INSERT INTO bc_rivalries (killer_uuid, victim_uuid, kills) VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE kills = kills + 1", killer.toString(), victim.toString());
    }

    /** Every kill count between two of {@code players}. */
    List<Rivalry> among(Collection<UUID> players) {
        if (players.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(players.size(), "?"));
        var ids = players.stream().map(UUID::toString).toList();
        return db.queryList("SELECT killer_uuid, victim_uuid, kills FROM bc_rivalries "
                        + "WHERE killer_uuid IN (" + placeholders + ") AND victim_uuid IN (" + placeholders + ")",
                row -> new Rivalry(UUID.fromString(row.getString("killer_uuid")), UUID.fromString(row.getString("victim_uuid")),
                        row.getInt("kills")),
                Stream.concat(ids.stream(), ids.stream()).toArray());
    }
}
