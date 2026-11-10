package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.player.PlayerReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class StrikeRepository {

    private final Database db;

    public StrikeRepository(Database db) {
        this.db = db;
    }

    public void add(UUID player, String reason, PlayerReference issuer) {
        db.update("INSERT INTO bc_strikes (player_uuid, reason, issuer, issuer_uuid, created_at) VALUES (?, ?, ?, ?, NOW())",
                player.toString(), reason, issuer.name(), PunishmentRepository.uuidOf(issuer));
    }

    public int count(UUID player) {
        return db.queryOne("SELECT COUNT(*) FROM bc_strikes WHERE player_uuid = ?", row -> row.getInt(1), player.toString()).orElse(0);
    }

    /** Oldest first, so index + 1 is the strike number shown to players. */
    public List<Strike> list(UUID player) {
        return db.queryList("SELECT id, player_uuid, reason, issuer, issuer_uuid, created_at FROM bc_strikes WHERE player_uuid = ? ORDER BY id ASC",
                StrikeRepository::map, player.toString());
    }

    public boolean removeLatest(UUID player) {
        return db.update("DELETE FROM bc_strikes WHERE player_uuid = ? ORDER BY id DESC LIMIT 1", player.toString()) > 0;
    }

    private static Strike map(ResultSet row) throws SQLException {
        return new Strike(row.getLong("id"), UUID.fromString(row.getString("player_uuid")), row.getString("reason"),
                PunishmentRepository.reference(row, "issuer", "issuer_uuid"), row.getTimestamp("created_at").toInstant());
    }
}
