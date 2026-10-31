package me.blockcraft.common.shop;

import me.blockcraft.common.db.Database;

import java.util.UUID;

/** Premium bought with coins in the lobby shop: one {@code bc_unlocks} row, granted as the LuckPerms group of that name. */
public final class Premium {

    public static final String ITEM = "lobby.premium";
    public static final String GROUP = "premium";

    private final Database db;

    public Premium(Database db) {
        this.db = db;
    }

    public boolean bought(UUID player) {
        return db.queryOne("SELECT 1 FROM bc_unlocks WHERE player_uuid = ? AND item = ?", row -> true, player.toString(), ITEM)
                .isPresent();
    }
}
