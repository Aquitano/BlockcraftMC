package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chat messages the advertisement filter blocked, for staff review via /werbung. */
public final class AdLogRepository {

    private final Database db;

    public AdLogRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_ad_log ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "server VARCHAR(64) NOT NULL, "
                + "message TEXT NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    public void add(UUID player, String server, String message) {
        db.update("INSERT INTO bc_ad_log (player_uuid, server, message, created_at) VALUES (?, ?, ?, NOW())",
                player.toString(), server, message);
    }

    public List<AdLog> list() {
        return db.queryList("SELECT id, player_uuid, server, message, created_at FROM bc_ad_log ORDER BY id ASC",
                row -> new AdLog(row.getLong("id"), UUID.fromString(row.getString("player_uuid")), row.getString("server"),
                        row.getString("message"), row.getTimestamp("created_at").toInstant()));
    }

    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM bc_ad_log", row -> row.getInt(1)).orElse(0);
    }

    public void clear() {
        db.update("DELETE FROM bc_ad_log");
    }
}
