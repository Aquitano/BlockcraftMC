package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Every timestamp is written and read on the database clock, so the proxy's own clock never shifts a login time. */
public final class PlayerRepository {

    private static final String COLUMNS = "uuid, name, UNIX_TIMESTAMP(first_seen) AS first_seen, "
            + "UNIX_TIMESTAMP(last_seen) AS last_seen, playtime_seconds";

    private final Database db;

    public PlayerRepository(Database db) {
        this.db = db;
    }

    public boolean exists(UUID player) {
        return db.queryOne("SELECT 1 FROM bc_players WHERE uuid = ?", row -> true, player.toString()).isPresent();
    }

    /** {@code first_seen} keeps the value the insert gave it, so a later login only moves {@code last_seen}. */
    public void upsert(UUID player, String name) {
        db.update("INSERT INTO bc_players (uuid, name, first_seen, last_seen) VALUES (?, ?, NOW(), NOW()) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = NOW()",
                player.toString(), name);
    }

    public void addPlaytime(UUID player, long seconds) {
        db.update("UPDATE bc_players SET playtime_seconds = playtime_seconds + ? WHERE uuid = ?", seconds, player.toString());
    }

    public Optional<KnownPlayer> byName(String name) {
        return db.queryOne("SELECT " + COLUMNS + " FROM bc_players WHERE name = ? ORDER BY last_seen DESC LIMIT 1",
                PlayerRepository::map, name);
    }

    public Optional<KnownPlayer> byUuid(UUID player) {
        return db.queryOne("SELECT " + COLUMNS + " FROM bc_players WHERE uuid = ?", PlayerRepository::map, player.toString());
    }

    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM bc_players", row -> row.getInt(1)).orElse(0);
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
