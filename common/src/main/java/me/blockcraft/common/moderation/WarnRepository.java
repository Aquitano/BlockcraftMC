package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WarnRepository {

    private final Database db;

    public WarnRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_warns ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "reason TEXT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_bc_warns_player (player_uuid))");
    }

    /** Returns the player's warn count after adding. */
    public int add(UUID player, Optional<String> reason) {
        db.update("INSERT INTO bc_warns (player_uuid, reason, created_at) VALUES (?, ?, NOW())",
                player.toString(), reason.orElse(null));
        return count(player);
    }

    public int count(UUID player) {
        return db.queryOne("SELECT COUNT(*) FROM bc_warns WHERE player_uuid = ?", row -> row.getInt(1), player.toString()).orElse(0);
    }

    /** Returns how many warns were removed. */
    public int clear(UUID player) {
        return db.update("DELETE FROM bc_warns WHERE player_uuid = ?", player.toString());
    }

    public void clearAll() {
        db.update("DELETE FROM bc_warns");
    }

    public List<WarnCount> counts() {
        return db.queryList("SELECT w.player_uuid, COALESCE(p.name, w.player_uuid) AS name, COUNT(*) AS warns "
                        + "FROM bc_warns w LEFT JOIN bc_players p ON p.uuid = w.player_uuid "
                        + "GROUP BY w.player_uuid, p.name ORDER BY warns DESC",
                row -> new WarnCount(UUID.fromString(row.getString("player_uuid")), row.getString("name"), row.getInt("warns")));
    }
}
