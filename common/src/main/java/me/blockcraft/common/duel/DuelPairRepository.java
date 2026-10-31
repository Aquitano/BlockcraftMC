package me.blockcraft.common.duel;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

/**
 * {@code bc_duel_pairs}: one row per pair of players who ever duelled, the two uuids ordered so a pair has one row
 * whoever is named first, with how many of their duels in a row ended in a draw.
 */
public final class DuelPairRepository {

    private record Pair(String first, String second) {

        static Pair of(UUID one, UUID two) {
            String a = one.toString();
            String b = two.toString();
            return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
        }
    }

    private final Database db;

    public DuelPairRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_duel_pairs ("
                + "first_uuid CHAR(36) NOT NULL, "
                + "second_uuid CHAR(36) NOT NULL, "
                + "draws_in_a_row INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (first_uuid, second_uuid))");
    }

    public boolean met(UUID one, UUID two) {
        var pair = Pair.of(one, two);
        return db.queryOne("SELECT 1 FROM bc_duel_pairs WHERE first_uuid = ? AND second_uuid = ?",
                row -> true, pair.first(), pair.second()).isPresent();
    }

    /**
     * Records a finished duel. Once the pair has drawn {@code rewardDraws} times in a row, both players get the unlock
     * {@code reward} in the same transaction. True if either did not own it yet.
     */
    public boolean finish(UUID one, UUID two, boolean draw, int rewardDraws, String reward) {
        var pair = Pair.of(one, two);
        return db.transaction(connection -> {
            try (var upsert = connection.prepareStatement("INSERT INTO bc_duel_pairs (first_uuid, second_uuid, draws_in_a_row) "
                    + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE draws_in_a_row = IF(VALUES(draws_in_a_row) = 0, 0, draws_in_a_row + 1)")) {
                upsert.setString(1, pair.first());
                upsert.setString(2, pair.second());
                upsert.setInt(3, draw ? 1 : 0);
                upsert.executeUpdate();
            }
            int draws;
            try (var select = connection.prepareStatement(
                    "SELECT draws_in_a_row FROM bc_duel_pairs WHERE first_uuid = ? AND second_uuid = ?")) {
                select.setString(1, pair.first());
                select.setString(2, pair.second());
                try (var rows = select.executeQuery()) {
                    draws = rows.next() ? rows.getInt(1) : 0;
                }
            }
            if (draws < rewardDraws) {
                return false;
            }
            int unlocked = 0;
            try (var unlock = connection.prepareStatement("INSERT IGNORE INTO bc_unlocks (player_uuid, item) VALUES (?, ?)")) {
                for (String player : List.of(pair.first(), pair.second())) {
                    unlock.setString(1, player);
                    unlock.setString(2, reward);
                    unlocked += unlock.executeUpdate();
                }
            }
            return unlocked > 0;
        });
    }
}
