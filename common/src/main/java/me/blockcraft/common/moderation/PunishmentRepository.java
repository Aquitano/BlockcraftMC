package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every timestamp is written and compared on the database clock and read back as epoch seconds, so the
 * proxy's and the game servers' time zones never shift an expiry.
 */
public final class PunishmentRepository {

    private static final String COLUMNS = "id, player_uuid, type, reason, issuer, UNIX_TIMESTAMP(created_at) AS created_at, "
            + "UNIX_TIMESTAMP(expires_at) AS expires_at, UNIX_TIMESTAMP(revoked_at) AS revoked_at, revoked_by";
    private static final String ACTIVE = "revoked_at IS NULL AND (expires_at IS NULL OR expires_at > NOW())";

    private final Database db;

    public PunishmentRepository(Database db) {
        this.db = db;
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
        db.update("INSERT INTO bc_punishments (player_uuid, type, reason, issuer, expires_at) "
                        + "VALUES (?, ?, ?, ?, NOW() + INTERVAL ? SECOND)",
                player.toString(), type.name(), reason, issuer, duration.map(Duration::toSeconds).orElse(null));
    }

    /** Ends every active punishment of that type; returns false if there was none. */
    public boolean revoke(UUID player, PunishmentType type, String revokedBy) {
        return db.update("UPDATE bc_punishments SET revoked_at = NOW(), revoked_by = ? WHERE player_uuid = ? AND type = ? AND " + ACTIVE,
                revokedBy, player.toString(), type.name()) > 0;
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
                Instant.ofEpochSecond(row.getLong("created_at")),
                epochSeconds(row, "expires_at"),
                epochSeconds(row, "revoked_at"),
                Optional.ofNullable(row.getString("revoked_by")));
    }

    private static Optional<Instant> epochSeconds(ResultSet row, String column) throws SQLException {
        long seconds = row.getLong(column);
        return row.wasNull() ? Optional.empty() : Optional.of(Instant.ofEpochSecond(seconds));
    }
}
