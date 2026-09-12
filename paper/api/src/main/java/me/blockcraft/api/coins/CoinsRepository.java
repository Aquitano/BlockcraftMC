package me.blockcraft.api.coins;

import me.blockcraft.common.db.Database;

import java.util.UUID;

public final class CoinsRepository {

    private final Database db;

    public CoinsRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS VCOINS (UUID VARCHAR(36) NOT NULL, NAME VARCHAR(16), COINS INT NOT NULL DEFAULT 0)");
    }

    public int get(UUID player) {
        return db.queryOne("SELECT COINS FROM VCOINS WHERE UUID = ?", row -> row.getInt("COINS"), player.toString()).orElse(0);
    }

    public boolean has(UUID player, int amount) {
        return get(player) >= amount;
    }

    public void set(UUID player, String name, int coins) {
        int updated = db.update("UPDATE VCOINS SET COINS = ?, NAME = ? WHERE UUID = ?", coins, name, player.toString());
        if (updated == 0) {
            db.update("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?)", player.toString(), name, coins);
        }
    }

    public void add(UUID player, String name, int delta) {
        set(player, name, get(player) + delta);
    }

    /** Returns false and leaves the balance untouched if the player cannot afford it. */
    public boolean withdraw(UUID player, String name, int amount) {
        int balance = get(player);
        if (balance < amount) {
            return false;
        }
        set(player, name, balance - amount);
        return true;
    }
}
