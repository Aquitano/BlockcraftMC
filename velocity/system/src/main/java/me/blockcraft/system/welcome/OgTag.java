package me.blockcraft.system.welcome;

import me.blockcraft.common.db.Database;

import java.util.UUID;

/** The shared migration snapshots OG eligibility; each eligible account is thanked once when it returns. */
public final class OgTag {

    /** What {@code ChatTagChoice.of(ChatTag.OG).item()} is on Paper, where the lobby's chat-tag menu reads it. */
    private static final String ITEM = "tag.og";

    private final Database db;

    public OgTag(Database db) {
        this.db = db;
    }

    public boolean thankIfKnown(UUID player) {
        boolean owns = db.queryOne("SELECT 1 FROM bc_unlocks WHERE player_uuid = ? AND item = ?", row -> true,
                player.toString(), ITEM).orElse(false);
        return owns && db.update("INSERT IGNORE INTO bc_unlocks (player_uuid, item) VALUES (?, 'tag.og.thanked')", player.toString()) > 0;
    }
}
