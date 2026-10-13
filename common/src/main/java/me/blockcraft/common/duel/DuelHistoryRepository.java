package me.blockcraft.common.duel;

import me.blockcraft.common.db.Database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class DuelHistoryRepository {

    record Previous(String opponent, boolean draw, String round) { }

    private final Database db;

    public DuelHistoryRepository(Database db) {
        this.db = db;
    }

    public boolean familiar(UUID player, UUID opponent) {
        return db.queryOne("SELECT 1 FROM bc_unlocks WHERE player_uuid = ? AND item = ?",
                row -> true, player.toString(), met(opponent)).isPresent();
    }

    public boolean finish(UUID round, UUID one, UUID two, boolean draw) {
        var players = List.of(one.toString(), two.toString()).stream().sorted().toList();
        return db.transaction(connection -> {
            try (var insert = connection.prepareStatement("INSERT IGNORE INTO bc_duel_history (player_uuid) VALUES (?)")) {
                for (String player : players) {
                    insert.setString(1, player);
                    insert.executeUpdate();
                }
            }
            var previous = new HashMap<String, Previous>();
            try (var query = connection.prepareStatement("SELECT player_uuid, opponent_uuid, last_draw, round_uuid "
                    + "FROM bc_duel_history WHERE player_uuid IN (?, ?) ORDER BY player_uuid FOR UPDATE")) {
                query.setString(1, players.getFirst());
                query.setString(2, players.getLast());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        previous.put(rows.getString("player_uuid"), new Previous(rows.getString("opponent_uuid"),
                                rows.getBoolean("last_draw"), rows.getString("round_uuid")));
                    }
                }
            }
            if (previous.values().stream().anyMatch(state -> round.toString().equals(state.round()))) {
                return false;
            }
            boolean mirror = consecutiveDraws(one, two, draw, previous.get(one.toString()), previous.get(two.toString()));
            try (var update = connection.prepareStatement("UPDATE bc_duel_history SET opponent_uuid = ?, last_draw = ?, round_uuid = ? "
                    + "WHERE player_uuid = ?")) {
                for (String player : players) {
                    update.setString(1, player.equals(one.toString()) ? two.toString() : one.toString());
                    update.setBoolean(2, draw);
                    update.setString(3, round.toString());
                    update.setString(4, player);
                    update.executeUpdate();
                }
            }
            unlock(connection, one, met(two));
            unlock(connection, two, met(one));
            if (!mirror) {
                return false;
            }
            boolean first = unlock(connection, one, "tag.spiegel");
            boolean second = unlock(connection, two, "tag.spiegel");
            return first || second;
        });
    }

    static boolean consecutiveDraws(UUID one, UUID two, boolean draw, Previous first, Previous second) {
        return draw && first.draw() && second.draw()
                && two.toString().equals(first.opponent()) && one.toString().equals(second.opponent());
    }

    private static boolean unlock(Connection connection, UUID player, String item) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT IGNORE INTO bc_unlocks (player_uuid, item) VALUES (?, ?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, item);
            return insert.executeUpdate() > 0;
        }
    }

    private static String met(UUID opponent) {
        return "duel.met." + opponent;
    }
}
