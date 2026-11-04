package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Which accounts logged in from the same address, kept as a keyed hash of the address and only for the retention
 * window; the raw address is never stored.
 */
public final class PlayerAddressRepository {

    public record Alt(UUID uuid, String name, Instant lastSeen, boolean banned, boolean muted) {
    }

    private static final String SHARED_FROM = "FROM bc_player_addresses mine "
            + "JOIN bc_player_addresses other ON other.address_hash = mine.address_hash AND other.player_uuid <> mine.player_uuid";
    private static final String SHARED_WHERE = "mine.player_uuid = ? AND mine.last_seen > NOW() - INTERVAL ? SECOND "
            + "AND other.last_seen > NOW() - INTERVAL ? SECOND";

    private final Database db;

    public PlayerAddressRepository(Database db) {
        this.db = db;
    }

    public void record(UUID player, byte[] addressHash) {
        db.update("INSERT INTO bc_player_addresses (player_uuid, address_hash, first_seen, last_seen) VALUES (?, ?, NOW(), NOW()) "
                + "ON DUPLICATE KEY UPDATE last_seen = NOW()", player.toString(), addressHash);
    }

    /** The other accounts that used one of the player's addresses, both within the retention window, last seen first. */
    public List<Alt> alts(UUID player, Duration retention) {
        return db.queryList("SELECT DISTINCT p.uuid, p.name, UNIX_TIMESTAMP(p.last_seen) AS seen, "
                        + active("p.uuid", PunishmentType.BAN) + " AS banned, " + active("p.uuid", PunishmentType.MUTE) + " AS muted "
                        + SHARED_FROM + " JOIN bc_players p ON p.uuid = other.player_uuid WHERE " + SHARED_WHERE + " ORDER BY seen DESC",
                PlayerAddressRepository::map, player.toString(), retention.toSeconds(), retention.toSeconds());
    }

    public boolean sharedWithBannedAccount(UUID player, byte[] addressHash, Duration retention) {
        return db.queryOne("SELECT 1 FROM bc_player_addresses other WHERE other.address_hash = ? AND other.player_uuid <> ? "
                        + "AND other.last_seen > NOW() - INTERVAL ? SECOND AND " + active("other.player_uuid", PunishmentType.BAN) + " LIMIT 1",
                row -> true, addressHash, player.toString(), retention.toSeconds()).isPresent();
    }

    public int purge(Duration retention) {
        return db.update("DELETE FROM bc_player_addresses WHERE last_seen < NOW() - INTERVAL ? SECOND", retention.toSeconds());
    }

    /** Counts the accounts sharing an address with the player that are banned right now; binds player, retention, retention. */
    static String bannedAltsQuery() {
        return "SELECT COUNT(DISTINCT other.player_uuid) " + SHARED_FROM + " WHERE " + SHARED_WHERE
                + " AND " + active("other.player_uuid", PunishmentType.BAN);
    }

    private static String active(String playerColumn, PunishmentType type) {
        return "EXISTS (SELECT 1 FROM bc_punishments WHERE player_uuid = " + playerColumn + " AND type = '" + type.name() + "' AND "
                + PunishmentRepository.ACTIVE + ")";
    }

    private static Alt map(ResultSet row) throws SQLException {
        return new Alt(UUID.fromString(row.getString("uuid")), row.getString("name"), Instant.ofEpochSecond(row.getLong("seen")),
                row.getBoolean("banned"), row.getBoolean("muted"));
    }
}
