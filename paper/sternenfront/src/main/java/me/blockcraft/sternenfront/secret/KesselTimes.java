package me.blockcraft.sternenfront.secret;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

/** Every player's best Kessel-Lauf in ticks, the one table Sternenfront keeps for its secrets. */
final class KesselTimes {

    private final Database db;

    KesselTimes(Database db) {
        this.db = db;
    }

    void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_sternenfront_kessel (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, ticks INT NOT NULL)");
    }

    List<BestTimes.Entry> best(int limit) {
        return db.queryList("SELECT player_uuid, name, ticks FROM bc_sternenfront_kessel ORDER BY ticks, name LIMIT ?",
                row -> new BestTimes.Entry(UUID.fromString(row.getString("player_uuid")), row.getString("name"), row.getLong("ticks")),
                limit);
    }

    /** Keeps the faster of the stored time and {@code ticks}, under the player's current name. */
    void record(UUID player, String name, long ticks) {
        db.update("INSERT INTO bc_sternenfront_kessel (player_uuid, name, ticks) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), ticks = LEAST(ticks, VALUES(ticks))", player.toString(), name, ticks);
    }
}
