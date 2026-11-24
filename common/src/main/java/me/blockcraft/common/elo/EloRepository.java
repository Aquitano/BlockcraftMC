package me.blockcraft.common.elo;

import me.blockcraft.common.db.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranked ratings in {@code bc_elo}, one row per player and season. A season nobody played yet has no rows, so
 * every player starts it fresh; past seasons stay as they ended.
 */
public final class EloRepository {

    private final Database db;
    private final EloFormula formula;

    public EloRepository(Database db, EloFormula formula) {
        this.db = db;
        this.formula = formula;
    }

    public EloFormula formula() {
        return formula;
    }

    public EloRating get(UUID player, Season season) {
        return db.queryOne("SELECT rating, wins, losses FROM bc_elo WHERE player_uuid = ? AND season = ?",
                EloRepository::rating, player.toString(), season.key()).orElse(EloRating.fresh(formula));
    }

    /** By the name the player carried at their last ranked round of the season. */
    public Optional<EloRating> byName(String name, Season season) {
        return db.queryOne("SELECT rating, wins, losses FROM bc_elo WHERE name = ? AND season = ?",
                EloRepository::rating, name, season.key());
    }

    public List<EloEntry> top(Season season, int limit) {
        return db.queryList("SELECT name, rating FROM bc_elo WHERE season = ? ORDER BY rating DESC, wins DESC LIMIT ?",
                row -> new EloEntry(row.getString("name"), row.getInt("rating")), season.key(), limit);
    }

    /** Blocking; both rows are locked while the step is computed, so concurrent rounds never lose a result. */
    public EloMatch settle(UUID winner, String winnerName, UUID loser, String loserName, Season season) {
        return db.transaction(connection -> {
            ensureRow(connection, winner, winnerName, season);
            ensureRow(connection, loser, loserName, season);
            var before = lock(connection, winner, loser, season);
            int delta = formula.delta(before.winner().rating(), before.loser().rating());
            var match = new EloMatch(delta, before.winner().won(delta), before.loser().lost(delta));
            write(connection, winner, winnerName, season, match.winner());
            write(connection, loser, loserName, season, match.loser());
            return match;
        });
    }

    private void ensureRow(Connection connection, UUID player, String name, Season season) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT IGNORE INTO bc_elo (player_uuid, season, rating, name) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, player.toString());
            statement.setString(2, season.key());
            statement.setInt(3, formula.startRating());
            statement.setString(4, name);
            statement.executeUpdate();
        }
    }

    /** One statement locks both rows in index order, whichever player is named first. */
    private static EloMatch lock(Connection connection, UUID winner, UUID loser, Season season) throws SQLException {
        EloRating winnerRating = null;
        EloRating loserRating = null;
        try (var statement = connection.prepareStatement(
                "SELECT player_uuid, rating, wins, losses FROM bc_elo WHERE player_uuid IN (?, ?) AND season = ? FOR UPDATE")) {
            statement.setString(1, winner.toString());
            statement.setString(2, loser.toString());
            statement.setString(3, season.key());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getString("player_uuid").equals(winner.toString())) {
                        winnerRating = rating(rows);
                    } else {
                        loserRating = rating(rows);
                    }
                }
            }
        }
        if (winnerRating == null || loserRating == null) {
            throw new SQLException("bc_elo rows missing after insert");
        }
        return new EloMatch(0, winnerRating, loserRating);
    }

    private static void write(Connection connection, UUID player, String name, Season season, EloRating rating) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE bc_elo SET rating = ?, wins = ?, losses = ?, name = ?, updated_at = NOW() WHERE player_uuid = ? AND season = ?")) {
            statement.setInt(1, rating.rating());
            statement.setInt(2, rating.wins());
            statement.setInt(3, rating.losses());
            statement.setString(4, name);
            statement.setString(5, player.toString());
            statement.setString(6, season.key());
            statement.executeUpdate();
        }
    }

    private static EloRating rating(ResultSet row) throws SQLException {
        return new EloRating(row.getInt("rating"), row.getInt("wins"), row.getInt("losses"));
    }
}
