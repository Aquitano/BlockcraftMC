package me.blockcraft.common.coins;

import me.blockcraft.common.db.Database;

import java.util.UUID;

/** Reads a player's coins from the legacy {@code VCOINS} table; a player without a row has none. */
public final class CoinBalanceRepository {

    private final Database db;

    public CoinBalanceRepository(Database db) {
        this.db = db;
    }

    public int balance(UUID player) {
        return db.queryOne("SELECT COINS FROM VCOINS WHERE UUID = ?", row -> row.getInt("COINS"), player.toString()).orElse(0);
    }
}
