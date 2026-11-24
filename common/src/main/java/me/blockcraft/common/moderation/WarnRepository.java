package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.player.PlayerReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WarnRepository {

    private final Database db;

    public WarnRepository(Database db) {
        this.db = db;
    }

    /** Returns the player's warn count after adding. */
    public int add(UUID player, Optional<String> reason, PlayerReference issuer) {
        db.update("INSERT INTO bc_warns (player_uuid, reason, issuer, issuer_uuid, created_at) VALUES (?, ?, ?, ?, NOW())",
                player.toString(), reason.orElse(null), issuer.name(), PunishmentRepository.uuidOf(issuer));
        return count(player);
    }

    public int count(UUID player) {
        return db.queryOne("SELECT COUNT(*) FROM bc_warns WHERE player_uuid = ?", row -> row.getInt(1), player.toString()).orElse(0);
    }

    /** Oldest first, like strikes. */
    public List<Warn> list(UUID player) {
        return db.queryList("SELECT id, player_uuid, reason, issuer, issuer_uuid, created_at FROM bc_warns WHERE player_uuid = ? ORDER BY id ASC",
                WarnRepository::map, player.toString());
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

    private static Warn map(ResultSet row) throws SQLException {
        var issuer = row.getString("issuer") == null
                ? Optional.<PlayerReference>empty()
                : Optional.of(PunishmentRepository.reference(row, "issuer", "issuer_uuid"));
        return new Warn(row.getLong("id"), UUID.fromString(row.getString("player_uuid")), Optional.ofNullable(row.getString("reason")),
                issuer, row.getTimestamp("created_at").toInstant());
    }
}
