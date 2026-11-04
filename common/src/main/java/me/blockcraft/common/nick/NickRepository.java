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

    public Optional<Nick> get(UUID player) {
        return db.queryOne("SELECT nick, skin_owner, rank_group FROM bc_nicks WHERE player_uuid = ?",
                row -> new Nick(row.getString("nick"), row.getString("skin_owner"), row.getString("rank_group")), player.toString());
    }

    /**
     * Stores {@code nick} as the one the player wears, unless another account wears it already or a known account is
     * called that, so a name typed into a command never means two players. A refused nick leaves the player's
     * previous one in place; answers whether it was stored.
     */
    public boolean claim(UUID player, Nick nick) {
        return db.transaction(connection -> {
            try (var known = connection.prepareStatement("SELECT 1 FROM bc_players WHERE name = ? AND uuid <> ?")) {
                known.setString(1, nick.name());
                known.setString(2, player.toString());
                try (var row = known.executeQuery()) {
                    if (row.next()) {
                        return false;
                    }
                }
            }
            try (var previous = connection.prepareStatement("DELETE FROM bc_nicks WHERE player_uuid = ?")) {
                previous.setString(1, player.toString());
                previous.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT IGNORE INTO bc_nicks (player_uuid, nick, skin_owner, rank_group) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, player.toString());
                insert.setString(2, nick.name());
                insert.setString(3, nick.skinOwner());
                insert.setString(4, nick.rankGroup());
                if (insert.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }
            }
            return true;
        });
    }

    /** Answers whether the player was wearing a nick at all. */
    public boolean clear(UUID player) {
        return db.update("DELETE FROM bc_nicks WHERE player_uuid = ?", player.toString()) > 0;
    }
}
