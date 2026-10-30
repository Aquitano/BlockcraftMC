package me.blockcraft.common.network;

import me.blockcraft.common.db.Database;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code bc_rejoins}: a game server keeps a running round's place for a player who dropped out, and the proxy sends
 * them back to that server while the place is kept. The database's clock decides when a place runs out, so the
 * servers' clocks need not agree.
 */
public final class RejoinRepository {

    private final Database db;

    public RejoinRepository(Database db) {
        this.db = db;
    }

    public void hold(UUID player, String server, int seconds) {
        db.update("""
                INSERT INTO bc_rejoins (player_uuid, server_name, until_at)
                VALUES (?, ?, TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP))
                ON DUPLICATE KEY UPDATE server_name = VALUES(server_name), until_at = VALUES(until_at)""",
                player.toString(), server, seconds);
    }

    public void release(UUID player, String server) {
        db.update("DELETE FROM bc_rejoins WHERE player_uuid = ? AND server_name = ?", player.toString(), server);
    }

    public void releaseAll(String server) {
        db.update("DELETE FROM bc_rejoins WHERE server_name = ?", server);
    }

    /** The server that still keeps the player's place. */
    public Optional<String> find(UUID player) {
        return db.queryOne("SELECT server_name FROM bc_rejoins WHERE player_uuid = ? AND until_at > CURRENT_TIMESTAMP",
                row -> row.getString("server_name"), player.toString());
    }
}
