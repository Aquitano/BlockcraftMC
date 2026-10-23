package me.blockcraft.common.welcome;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

/**
 * Who greeted whom on their first visit, in {@code bc_greetings}. The backend that saw the greeting writes it and
 * the proxy reads it back for the newcomer; a greeter counts once per newcomer, under the name others saw then.
 */
public final class GreetingRepository {

    private final Database db;

    public GreetingRepository(Database db) {
        this.db = db;
    }

    public enum Outcome { ALREADY_GREETED, PAID, UNPAID }

    public Outcome reward(UUID greeter, String shownName, String accountName, UUID newcomer, int amount, int dailyCap) {
        return db.transaction(connection -> {
            try (var lock = connection.prepareStatement("SELECT uuid FROM bc_players WHERE uuid = ? FOR UPDATE")) {
                lock.setString(1, greeter.toString());
                try (var row = lock.executeQuery()) {
                    if (!row.next()) { return Outcome.ALREADY_GREETED; }
                }
            }
            try (var insert = connection.prepareStatement("INSERT IGNORE INTO bc_greetings (greeter_uuid, newcomer_uuid, greeter_name) VALUES (?, ?, ?)")) {
                insert.setString(1, greeter.toString());
                insert.setString(2, newcomer.toString());
                insert.setString(3, shownName);
                if (insert.executeUpdate() == 0) { return Outcome.ALREADY_GREETED; }
            }
            try (var count = connection.prepareStatement("SELECT COUNT(*) FROM bc_greetings WHERE greeter_uuid = ? AND greeted_at >= CURDATE()")) {
                count.setString(1, greeter.toString());
                try (var row = count.executeQuery()) {
                    row.next();
                    if (row.getInt(1) > dailyCap || amount <= 0) { return Outcome.UNPAID; }
                }
            }
            try (var credit = connection.prepareStatement("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), COINS = COINS + VALUES(COINS)")) {
                credit.setString(1, greeter.toString());
                credit.setString(2, accountName);
                credit.setInt(3, amount);
                credit.executeUpdate();
            }
            return Outcome.PAID;
        });
    }

    /** The names the newcomer's greeters were seen under, first greeting first. */
    public List<String> greeters(UUID newcomer) {
        return db.queryList("SELECT greeter_name FROM bc_greetings WHERE newcomer_uuid = ? ORDER BY greeted_at, greeter_name",
                row -> row.getString("greeter_name"), newcomer.toString());
    }

    /** How many newcomers the player has greeted. */
    public int greetedBy(UUID greeter) {
        return db.queryOne("SELECT COUNT(*) FROM bc_greetings WHERE greeter_uuid = ?", row -> row.getInt(1), greeter.toString()).orElse(0);
    }
}
