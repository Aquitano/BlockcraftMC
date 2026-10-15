package me.blockcraft.common.coins;

import me.blockcraft.common.db.Database;
import me.blockcraft.common.moderation.PlayerRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CoinsRepository {

    public record Account(UUID uuid, String name) {
    }

    public record Balance(String name, int coins) {
    }

    private final Database db;
    private final PlayerRepository players;

    public CoinsRepository(Database db) {
        this.db = db;
        this.players = new PlayerRepository(db);
    }

    public int get(UUID player) {
        return db.queryOne("SELECT COINS FROM VCOINS WHERE UUID = ?", row -> row.getInt("COINS"), player.toString()).orElse(0);
    }

    /** Whoever last logged in under {@code name}, or else a legacy account that still carries it. */
    public Optional<Account> account(String name) {
        return players.byName(name).map(known -> new Account(known.uuid(), known.name()))
                .or(() -> db.queryOne("SELECT UUID, NAME FROM VCOINS WHERE NAME = ? LIMIT 1",
                        row -> new Account(UUID.fromString(row.getString("UUID")), row.getString("NAME")), name));
    }

    public List<Balance> top(int limit) {
        return db.queryList("SELECT NAME, COINS FROM VCOINS WHERE NAME IS NOT NULL ORDER BY COINS DESC LIMIT ?",
                row -> new Balance(row.getString("NAME"), row.getInt("COINS")), limit);
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
