package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.util.Optional;
import java.util.UUID;

/** Players frozen by staff; the flag follows them across servers until a staff member lifts it. */
public final class FreezeRepository {

    private final Database db;

    public FreezeRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_frozen ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "issuer VARCHAR(16) NOT NULL, "
                + "since TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    public Optional<String> issuer(UUID player) {
        return db.queryOne("SELECT issuer FROM bc_frozen WHERE player_uuid = ?", row -> row.getString("issuer"), player.toString());
    }

    public void freeze(UUID player, String issuer) {
        db.update("INSERT INTO bc_frozen (player_uuid, issuer) VALUES (?, ?) ON DUPLICATE KEY UPDATE issuer = VALUES(issuer), since = NOW()",
                player.toString(), issuer);
    }

    public void unfreeze(UUID player) {
        db.update("DELETE FROM bc_frozen WHERE player_uuid = ?", player.toString());
    }
}
