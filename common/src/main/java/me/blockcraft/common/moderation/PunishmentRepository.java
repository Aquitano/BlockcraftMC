package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.player.PlayerReference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every timestamp is written and compared on the database clock and read back as epoch seconds, so the
 * proxy's and the game servers' time zones never shift an expiry. The issuer is stored by account and by the
 * name it had at the time; the console has only a name.
 */
public final class PunishmentRepository {

    private static final String COLUMNS = "id, player_uuid, type, reason, issuer, issuer_uuid, UNIX_TIMESTAMP(created_at) AS created_at, "
            + "UNIX_TIMESTAMP(expires_at) AS expires_at, UNIX_TIMESTAMP(revoked_at) AS revoked_at, revoked_by, revoked_by_uuid";
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

    public void ban(UUID player, String reason, PlayerReference issuer, Optional<Duration> duration) {
        create(player, PunishmentType.BAN, reason, issuer, duration);
    }

    public void mute(UUID player, String reason, PlayerReference issuer, Optional<Duration> duration) {
        create(player, PunishmentType.MUTE, reason, issuer, duration);
    }

    public void create(UUID player, PunishmentType type, String reason, PlayerReference issuer, Optional<Duration> duration) {
        db.update("INSERT INTO bc_punishments (player_uuid, type, reason, issuer, issuer_uuid, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, NOW() + INTERVAL ? SECOND)",
                player.toString(), type.name(), reason, issuer.name(), uuidOf(issuer), duration.map(Duration::toSeconds).orElse(null));
    }

    /** Ends every active punishment of that type; returns false if there was none. */
    public boolean revoke(UUID player, PunishmentType type, PlayerReference revokedBy) {
        return db.update("UPDATE bc_punishments SET revoked_at = NOW(), revoked_by = ?, revoked_by_uuid = ? "
                        + "WHERE player_uuid = ? AND type = ? AND " + ACTIVE,
                revokedBy.name(), uuidOf(revokedBy), player.toString(), type.name()) > 0;
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
                reference(row, "issuer", "issuer_uuid"),
                Instant.ofEpochSecond(row.getLong("created_at")),
                epochSeconds(row, "expires_at"),
                epochSeconds(row, "revoked_at"),
                row.getString("revoked_by") == null ? Optional.empty() : Optional.of(reference(row, "revoked_by", "revoked_by_uuid")));
    }

    static PlayerReference reference(ResultSet row, String nameColumn, String uuidColumn) throws SQLException {
        return new PlayerReference(row.getString(nameColumn), Optional.ofNullable(row.getString(uuidColumn)).map(UUID::fromString));
    }

    static String uuidOf(PlayerReference reference) {
        return reference.uuid().map(UUID::toString).orElse(null);
    }

    private static Optional<Instant> epochSeconds(ResultSet row, String column) throws SQLException {
        long seconds = row.getLong(column);
        return row.wasNull() ? Optional.empty() : Optional.of(Instant.ofEpochSecond(seconds));
    }
}
