package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class PlayerRepository {

    private final Database db;

    public PlayerRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_players ("
                + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, "
                + "last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_bc_players_name (name))");
    }

    public boolean exists(UUID player) {
        return db.queryOne("SELECT 1 FROM bc_players WHERE uuid = ?", row -> true, player.toString()).isPresent();
    }

    public void upsert(UUID player, String name) {
        db.update("INSERT INTO bc_players (uuid, name, last_seen) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)",
                player.toString(), name, Timestamp.from(Instant.now()));
    }

    public Optional<KnownPlayer> byName(String name) {
        return db.queryOne("SELECT uuid, name, last_seen FROM bc_players WHERE name = ? ORDER BY last_seen DESC LIMIT 1",
                PlayerRepository::map, name);
    }

    public Optional<KnownPlayer> byUuid(UUID player) {
        return db.queryOne("SELECT uuid, name, last_seen FROM bc_players WHERE uuid = ?", PlayerRepository::map, player.toString());
    }

    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM bc_players", row -> row.getInt(1)).orElse(0);
    }

    private static KnownPlayer map(ResultSet row) throws SQLException {
        return new KnownPlayer(UUID.fromString(row.getString("uuid")), row.getString("name"), row.getTimestamp("last_seen").toInstant());
    }
}
