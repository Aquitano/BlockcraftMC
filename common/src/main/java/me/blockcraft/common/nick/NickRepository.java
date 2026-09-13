package me.blockcraft.common.nick;

import me.blockcraft.common.db.Database;

import java.util.Optional;
import java.util.UUID;

/** The nick a player currently wears, network-wide, so every server re-applies it on join. */
public final class NickRepository {

    private final Database db;

    public NickRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_nicks ("
                + "player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "nick VARCHAR(16) NOT NULL, "
                + "skin_owner VARCHAR(16) NOT NULL, "
                + "set_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    public Optional<Nick> get(UUID player) {
        return db.queryOne("SELECT nick, skin_owner FROM bc_nicks WHERE player_uuid = ?",
                row -> new Nick(row.getString("nick"), row.getString("skin_owner")), player.toString());
    }

    public void set(UUID player, Nick nick) {
        db.update("INSERT INTO bc_nicks (player_uuid, nick, skin_owner) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE nick = VALUES(nick), skin_owner = VALUES(skin_owner), set_at = NOW()",
                player.toString(), nick.name(), nick.skinOwner());
    }

    public void clear(UUID player) {
        db.update("DELETE FROM bc_nicks WHERE player_uuid = ?", player.toString());
    }
}
