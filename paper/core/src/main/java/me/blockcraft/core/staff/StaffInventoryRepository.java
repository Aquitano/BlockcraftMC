package me.blockcraft.core.staff;

import me.blockcraft.common.db.Database;

import java.util.Optional;
import java.util.UUID;

/** The inventory a player had before entering staff mode, so a crash or quit mid-session loses nothing. */
public final class StaffInventoryRepository {

    private final Database db;
    private final String server;

    /** Rows belong to the server that holds the session: on a switch the next server logs the player in first. */
    public StaffInventoryRepository(Database db, String server) {
        this.db = db;
        this.server = server;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_staff_inventories ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "contents BLOB NOT NULL, "
                + "saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        db.execute("ALTER TABLE bc_staff_inventories ADD COLUMN IF NOT EXISTS server VARCHAR(64) NOT NULL DEFAULT ''");
    }

    public void save(UUID player, byte[] contents) {
        db.update("INSERT INTO bc_staff_inventories (player_uuid, server, contents) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE server = VALUES(server), contents = VALUES(contents), saved_at = NOW()",
                player.toString(), server, contents);
    }

    public Optional<byte[]> find(UUID player) {
        return db.queryOne("SELECT contents FROM bc_staff_inventories WHERE player_uuid = ? AND server IN (?, '')",
                row -> row.getBytes("contents"), player.toString(), server);
    }

    public void delete(UUID player) {
        db.update("DELETE FROM bc_staff_inventories WHERE player_uuid = ? AND server IN (?, '')", player.toString(), server);
    }
}
