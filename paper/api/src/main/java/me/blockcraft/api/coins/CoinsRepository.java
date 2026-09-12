package me.blockcraft.api.coins;

import me.blockcraft.common.db.Database;

import java.util.UUID;

public final class CoinsRepository {

    private final Database db;

    public CoinsRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS VCOINS (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), COINS INT NOT NULL DEFAULT 0)");
    }

    public int get(UUID player) {
        return db.queryOne("SELECT COINS FROM VCOINS WHERE UUID = ?", row -> row.getInt("COINS"), player.toString()).orElse(0);
    }

    /** Opens the account with the starting balance on first sight; afterwards only the name is refreshed. */
    public void register(UUID player, String name, int startingCoins) {
        db.update("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE NAME = VALUES(NAME)",
                player.toString(), name, startingCoins);
    }

    public boolean has(UUID player, int amount) {
        return get(player) >= amount;
    }

    public void set(UUID player, String name, int coins) {
        db.update("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), COINS = VALUES(COINS)",
                player.toString(), name, coins);
    }

    public void add(UUID player, String name, int delta) {
        db.update("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), COINS = COINS + ?",
                player.toString(), name, delta, delta);
    }

    /** Returns false and leaves the balance untouched if the player cannot afford it. */
    public boolean withdraw(UUID player, String name, int amount) {
        return db.update("UPDATE VCOINS SET COINS = COINS - ?, NAME = ? WHERE UUID = ? AND COINS >= ?",
                amount, name, player.toString(), amount) > 0;
    }
}
