package me.blockcraft.ttt.game;

import me.blockcraft.common.db.Database;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every player's karma, the one table TTT keeps for itself. */
public final class KarmaRepository {

    private final Database db;

    public KarmaRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_ttt_karma (UUID VARCHAR(36) NOT NULL PRIMARY KEY, KARMA INT NOT NULL)");
    }

    public Optional<Integer> get(UUID player) {
        return db.queryOne("SELECT KARMA FROM bc_ttt_karma WHERE UUID = ?", row -> row.getInt("KARMA"), player.toString());
    }

    public void save(Map<UUID, Integer> karma) {
        karma.forEach((player, value) -> db.update(
                "INSERT INTO bc_ttt_karma (UUID, KARMA) VALUES (?, ?) ON DUPLICATE KEY UPDATE KARMA = VALUES(KARMA)",
                player.toString(), value));
    }
}
