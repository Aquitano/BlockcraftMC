package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Every timestamp is written and read on the database clock, so the proxy's own clock never shifts a login time. */
public final class PlayerRepository {

    private static final String COLUMNS = "uuid, name, UNIX_TIMESTAMP(first_seen) AS first_seen, "
            + "UNIX_TIMESTAMP(last_seen) AS last_seen, playtime_seconds";

    private final Database db;

    public PlayerRepository(Database db) {
        this.db = db;
    }

    /** {@code first_seen} keeps the value the insert gave it, so a later login only moves {@code last_seen}. */
    public void upsert(UUID player, String name) {
        db.update("INSERT INTO bc_players (uuid, name, first_seen, last_seen, first_seen_verified) VALUES (?, ?, NOW(), NOW(), TRUE) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = NOW()",
                player.toString(), name);
    }

    public OptionalInt assignJoinNumber(UUID player) {
        return db.transaction(connection -> {
            int next;
            try (var lock = connection.prepareStatement("SELECT next_number FROM bc_join_counter WHERE id = 1 FOR UPDATE");
                 var row = lock.executeQuery()) {
                row.next();
                next = row.getInt(1);
            }
            try (var select = connection.prepareStatement("SELECT join_number FROM bc_players WHERE uuid = ?")) {
                select.setString(1, player.toString());
                try (var row = select.executeQuery()) {
                    if (!row.next()) { return OptionalInt.empty(); }
                    int current = row.getInt(1);
                    if (!row.wasNull()) { return OptionalInt.of(current); }
                }
            }
            try (var update = connection.prepareStatement("UPDATE bc_players SET join_number = ? WHERE uuid = ?")) {
                update.setInt(1, next);
                update.setString(2, player.toString());
                update.executeUpdate();
            }
            try (var advance = connection.prepareStatement("UPDATE bc_join_counter SET next_number = next_number + 1 WHERE id = 1")) {
                advance.executeUpdate();
            }
            return OptionalInt.of(next);
        });
    }

    public boolean verifiedFirstSeen(UUID player) {
        return db.queryOne("SELECT first_seen_verified FROM bc_players WHERE uuid = ?", row -> row.getBoolean(1), player.toString()).orElse(false);
    }

    /** Called when a session ends, which is the last moment the player was seen. */
    public void addPlaytime(UUID player, long seconds) {
        db.update("UPDATE bc_players SET playtime_seconds = playtime_seconds + ?, last_seen = NOW() WHERE uuid = ?", seconds, player.toString());
    }

    public Optional<KnownPlayer> byName(String name) {
        return db.queryOne("SELECT " + COLUMNS + " FROM bc_players WHERE name = ? ORDER BY last_seen DESC LIMIT 1",
                PlayerRepository::map, name);
    }

    public Optional<KnownPlayer> byUuid(UUID player) {
        return db.queryOne("SELECT " + COLUMNS + " FROM bc_players WHERE uuid = ?", PlayerRepository::map, player.toString());
    }

    /** The account that most recently logged in from the address, by the hash {@code bc_player_addresses} keeps of it. */
    public Optional<KnownPlayer> lastSeenOn(byte[] addressHash) {
        return db.queryOne("SELECT " + COLUMNS + " FROM bc_players WHERE uuid = (SELECT player_uuid FROM bc_player_addresses "
                + "WHERE address_hash = ? ORDER BY last_seen DESC LIMIT 1)", PlayerRepository::map, addressHash);
    }

    private static KnownPlayer map(ResultSet row) throws SQLException {
        return new KnownPlayer(
                UUID.fromString(row.getString("uuid")),
                row.getString("name"),
                Instant.ofEpochSecond(row.getLong("first_seen")),
                Instant.ofEpochSecond(row.getLong("last_seen")),
                Duration.ofSeconds(row.getLong("playtime_seconds")));
    }
}
