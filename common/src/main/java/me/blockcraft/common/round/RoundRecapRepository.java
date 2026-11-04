package me.blockcraft.common.round;

import me.blockcraft.common.db.Database;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One recap per player in {@code bc_round_recaps}: the game writes it at the end of a round, the lobby takes it. */
public final class RoundRecapRepository {

    private final Database db;

    public RoundRecapRepository(Database db) {
        this.db = db;
    }

    public void save(Map<UUID, RoundRecap> recaps) {
        recaps.forEach((player, recap) -> db.update(
                "INSERT INTO bc_round_recaps (player_uuid, game, won, winners, kills, deaths, best_streak, coins, xp, ended_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE game = VALUES(game), won = VALUES(won), winners = VALUES(winners), "
                        + "kills = VALUES(kills), deaths = VALUES(deaths), best_streak = VALUES(best_streak), "
                        + "coins = VALUES(coins), xp = VALUES(xp), ended_at = NOW()",
                player.toString(), recap.game(), recap.won(), recap.winners(), recap.kills(), recap.deaths(),
                recap.bestStreak(), recap.coins(), recap.xp()));
    }

    /** The recap waiting for the player if its round ended within {@code maxAge}; whatever waited is gone afterwards. */
    public Optional<RoundRecap> take(UUID player, Duration maxAge) {
        var recap = db.queryOne(
                "SELECT game, won, winners, kills, deaths, best_streak, coins, xp FROM bc_round_recaps "
                        + "WHERE player_uuid = ? AND TIMESTAMPDIFF(SECOND, ended_at, NOW()) <= ?",
                row -> new RoundRecap(row.getString("game"), row.getBoolean("won"), row.getString("winners"),
                        row.getInt("kills"), row.getInt("deaths"), row.getInt("best_streak"), row.getInt("coins"), row.getInt("xp")),
                player.toString(), maxAge.toSeconds());
        db.update("DELETE FROM bc_round_recaps WHERE player_uuid = ?", player.toString());
        return recap;
    }
}
