package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Who is vanished network-wide; every Paper server hides them on join and the proxy can leave them out of lists. */
public final class VanishRepository {

    private final Database db;

    public VanishRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_vanished ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "since TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    public boolean isVanished(UUID player) {
        return db.queryOne("SELECT 1 FROM bc_vanished WHERE player_uuid = ?", row -> true, player.toString()).isPresent();
    }

    public Set<UUID> all() {
        return db.queryList("SELECT player_uuid FROM bc_vanished", row -> UUID.fromString(row.getString("player_uuid"))).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    public void set(UUID player, boolean vanished) {
        if (vanished) {
            db.update("INSERT IGNORE INTO bc_vanished (player_uuid) VALUES (?)", player.toString());
        } else {
            db.update("DELETE FROM bc_vanished WHERE player_uuid = ?", player.toString());
        }
    }
}
