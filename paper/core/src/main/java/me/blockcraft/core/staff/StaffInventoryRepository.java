package me.blockcraft.core.staff;

import me.blockcraft.common.db.Database;

import java.util.Optional;
import java.util.UUID;

/** The inventory a player had before entering staff mode, so a crash or quit mid-session loses nothing. */
public final class StaffInventoryRepository {

    private final Database db;

    public StaffInventoryRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_staff_inventories ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "contents BLOB NOT NULL, "
                + "saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    public void save(UUID player, byte[] contents) {
        db.update("INSERT INTO bc_staff_inventories (player_uuid, contents) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE contents = VALUES(contents), saved_at = NOW()", player.toString(), contents);
    }

    public Optional<byte[]> find(UUID player) {
        return db.queryOne("SELECT contents FROM bc_staff_inventories WHERE player_uuid = ?", row -> row.getBytes("contents"), player.toString());
    }

    public void delete(UUID player) {
        db.update("DELETE FROM bc_staff_inventories WHERE player_uuid = ?", player.toString());
    }
}
