package me.blockcraft.common.xp;

import me.blockcraft.common.db.Database;

import java.util.UUID;

/** Network-wide experience points, one row per player; the level is derived from the total. */
public final class XpRepository {

    private final Database db;

    public XpRepository(Database db) {
        this.db = db;
    }

    public long get(UUID player) {
        return db.queryOne("SELECT xp FROM bc_xp WHERE player_uuid = ?", row -> row.getLong("xp"), player.toString()).orElse(0L);
    }

    /** Returns the total after the change. */
    public long add(UUID player, long amount) {
        db.update("INSERT INTO bc_xp (player_uuid, xp) VALUES (?, ?) ON DUPLICATE KEY UPDATE xp = xp + ?",
                player.toString(), amount, amount);
        return get(player);
    }
}
