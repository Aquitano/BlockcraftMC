package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentRepository {

    private static final String COLUMNS = "id, player_uuid, type, reason, issuer, created_at, expires_at, revoked_at, revoked_by";
    private static final String ACTIVE = "revoked_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())";

    private final Database db;

    public PunishmentRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_punishments ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid CHAR(36) NOT NULL, "
                + "type ENUM('BAN','MUTE') NOT NULL, "
                + "reason TEXT NOT NULL, "
                + "issuer VARCHAR(16) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "expires_at TIMESTAMP NULL, "
                + "revoked_at TIMESTAMP NULL, "
                + "revoked_by VARCHAR(16) NULL, "
                + "INDEX idx_bc_punishments_player (player_uuid, type))");
    }

    public Optional<Punishment> active(UUID player, PunishmentType type) {
        return db.queryOne("SELECT " + COLUMNS + " FROM bc_punishments WHERE player_uuid = ? AND type = ? AND " + ACTIVE
                + " ORDER BY id DESC LIMIT 1", PunishmentRepository::map, player.toString(), type.name());
    }

    public Optional<Punishment> activeBan(UUID player) {
        return active(player, PunishmentType.BAN);
    }

    public Optional<Punishment> activeMute(UUID player) {
        return active(player, PunishmentType.MUTE);
    }

    public void ban(UUID player, String reason, String issuer, Optional<Duration> duration) {
        create(player, PunishmentType.BAN, reason, issuer, duration);
    }

    public void mute(UUID player, String reason, String issuer, Optional<Duration> duration) {
        create(player, PunishmentType.MUTE, reason, issuer, duration);
    }

    public void create(UUID player, PunishmentType type, String reason, String issuer, Optional<Duration> duration) {
        var now = Instant.now();
        db.update("INSERT INTO bc_punishments (player_uuid, type, reason, issuer, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                player.toString(), type.name(), reason, issuer, Timestamp.from(now),
                duration.map(now::plus).map(Timestamp::from).orElse(null));
    }

    /** Ends every active punishment of that type; returns false if there was none. */
    public boolean revoke(UUID player, PunishmentType type, String revokedBy) {
        return db.update("UPDATE bc_punishments SET revoked_at = ?, revoked_by = ? WHERE player_uuid = ? AND type = ? AND " + ACTIVE,
                Timestamp.from(Instant.now()), revokedBy, player.toString(), type.name()) > 0;
    }

    public List<Punishment> history(UUID player, PunishmentType type) {
        return db.queryList("SELECT " + COLUMNS + " FROM bc_punishments WHERE player_uuid = ? AND type = ? ORDER BY id DESC",
                PunishmentRepository::map, player.toString(), type.name());
    }

    public List<Punishment> listActive(PunishmentType type) {
        return db.queryList("SELECT " + COLUMNS + " FROM bc_punishments WHERE type = ? AND " + ACTIVE + " ORDER BY id DESC",
                PunishmentRepository::map, type.name());
    }

    private static Punishment map(ResultSet row) throws SQLException {
        return new Punishment(
                row.getLong("id"),
                UUID.fromString(row.getString("player_uuid")),
                PunishmentType.valueOf(row.getString("type")),
                row.getString("reason"),
                row.getString("issuer"),
                row.getTimestamp("created_at").toInstant(),
                Optional.ofNullable(row.getTimestamp("expires_at")).map(Timestamp::toInstant),
                Optional.ofNullable(row.getTimestamp("revoked_at")).map(Timestamp::toInstant),
                Optional.ofNullable(row.getString("revoked_by")));
    }
}
