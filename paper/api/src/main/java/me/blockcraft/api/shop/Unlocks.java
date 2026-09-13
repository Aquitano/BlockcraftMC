package me.blockcraft.api.shop;

import me.blockcraft.common.db.Database;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everything a player has unlocked anywhere on the network, one row per item. Items are namespaced keys such as
 * {@code lobby.head.mario} or {@code qsg.kit.bow}, so every plugin shares the table without sharing an enum.
 */
public final class Unlocks {

    private final Database db;

    public Unlocks(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_unlocks ("
                + "player_uuid CHAR(36) NOT NULL, "
                + "item VARCHAR(64) NOT NULL, "
                + "unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (player_uuid, item))");
    }

    public boolean owns(UUID player, String item) {
        return db.queryOne("SELECT 1 FROM bc_unlocks WHERE player_uuid = ? AND item = ?", row -> true, player.toString(), item).isPresent();
    }

    /** The full item keys the player owns under a namespace prefix such as {@code lobby.head.}. */
    public Set<String> owned(UUID player, String prefix) {
        return db.queryList("SELECT item FROM bc_unlocks WHERE player_uuid = ? AND item LIKE ?", row -> row.getString("item"),
                        player.toString(), escapeLike(prefix) + "%").stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns false if the player already owned the item. */
    public boolean unlock(UUID player, String item) {
        return db.update("INSERT IGNORE INTO bc_unlocks (player_uuid, item) VALUES (?, ?)", player.toString(), item) > 0;
    }

    void revoke(UUID player, String item) {
        db.update("DELETE FROM bc_unlocks WHERE player_uuid = ? AND item = ?", player.toString(), item);
    }

    /**
     * Copies the flag columns of a legacy per-game kit table ({@code UUID, KIT1, KIT2, ...}) into this table.
     * Idempotent, and a no-op when the legacy table does not exist.
     */
    public void importLegacyTable(String table, Map<String, String> itemByColumn) {
        boolean exists = db.queryOne("SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                row -> true, table).isPresent();
        if (!exists) {
            return;
        }
        itemByColumn.forEach((column, item) -> db.update(
                "INSERT IGNORE INTO bc_unlocks (player_uuid, item) SELECT UUID, ? FROM " + table + " WHERE " + column + " = 1", item));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
