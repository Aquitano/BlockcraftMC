package me.blockcraft.common.shop;

import me.blockcraft.common.db.Database;

import java.sql.SQLException;
import java.util.UUID;

/** A purchase commits the unlock and payment together, including when different backends sell concurrently. */
public final class Shop {

    public enum Outcome {
        BOUGHT,
        ALREADY_OWNED,
        NOT_ENOUGH_COINS
    }

    /** {@code balance} is the coins after the purchase, or the unchanged balance when nothing was bought. */
    public record Purchase(Outcome outcome, int balance) {

        public boolean bought() {
            return outcome == Outcome.BOUGHT;
        }
    }

    private static final int DUPLICATE_KEY = 1062;

    private final Database db;

    public Shop(Database db) {
        this.db = db;
    }

    /** Blocking; call it through the platform's async task helper. The coin row is locked for the whole purchase. */
    public Purchase buy(UUID player, String name, String item, int price) {
        if (price < 0 || item.isBlank() || item.length() > 64) {
            throw new IllegalArgumentException("Invalid purchase");
        }
        return db.transaction(connection -> {
            int balance = 0;
            boolean account;
            try (var statement = connection.prepareStatement("SELECT COINS FROM VCOINS WHERE UUID = ? FOR UPDATE")) {
                statement.setString(1, player.toString());
                try (var rows = statement.executeQuery()) {
                    account = rows.next();
                    if (account) {
                        balance = rows.getInt(1);
                    }
                }
            }
            try (var statement = connection.prepareStatement("SELECT 1 FROM bc_unlocks WHERE player_uuid = ? AND item = ?")) {
                statement.setString(1, player.toString());
                statement.setString(2, item);
                try (var rows = statement.executeQuery()) {
                    if (rows.next()) {
                        return new Purchase(Outcome.ALREADY_OWNED, balance);
                    }
                }
            }
            if (!account || balance < price) {
                return new Purchase(Outcome.NOT_ENOUGH_COINS, balance);
            }
            try (var statement = connection.prepareStatement("INSERT INTO bc_unlocks (player_uuid, item) VALUES (?, ?)")) {
                statement.setString(1, player.toString());
                statement.setString(2, item);
                try {
                    statement.executeUpdate();
                } catch (SQLException e) {
                    if (e.getErrorCode() == DUPLICATE_KEY) {
                        return new Purchase(Outcome.ALREADY_OWNED, balance);
                    }
                    throw e;
                }
            }
            try (var statement = connection.prepareStatement("UPDATE VCOINS SET COINS = COINS - ?, NAME = ? WHERE UUID = ?")) {
                statement.setInt(1, price);
                statement.setString(2, name);
                statement.setString(3, player.toString());
                statement.executeUpdate();
            }
            return new Purchase(Outcome.BOUGHT, balance - price);
        });
    }
}
