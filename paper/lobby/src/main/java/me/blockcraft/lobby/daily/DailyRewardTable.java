package me.blockcraft.lobby.daily;

import me.blockcraft.common.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** {@code bc_daily_rewards}: per player the streak day of the last claim and its date. Blocking. */
public final class DailyRewardTable {

    private static final String LAST_CLAIM = "SELECT streak, last_claim FROM bc_daily_rewards WHERE player_uuid = ?";

    private final Database db;
    private final DailyRewards rewards;

    public DailyRewardTable(Database db, DailyRewards rewards) {
        this.db = db;
        this.rewards = rewards;
        db.execute("CREATE TABLE IF NOT EXISTS bc_daily_rewards (player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "streak INT NOT NULL DEFAULT 0, last_claim DATE NULL)");
    }

    public DailyRewards.Today today(UUID player, LocalDate today) {
        return rewards.today(db.queryOne(LAST_CLAIM, DailyRewardTable::claimed, player.toString()), today);
    }

    /**
     * Pays today's reward and moves the streak on in one transaction; the player's row stays locked until it commits,
     * so a second lobby claiming at the same moment waits and then finds the day taken. Empty when it already was.
     */
    public Optional<DailyRewards.Today> claim(UUID player, String name, LocalDate today) {
        db.update("INSERT IGNORE INTO bc_daily_rewards (player_uuid) VALUES (?)", player.toString());
        return db.transaction(connection -> {
            Optional<DailyRewards.Claimed> last;
            try (var statement = connection.prepareStatement(LAST_CLAIM + " FOR UPDATE")) {
                statement.setString(1, player.toString());
                try (var rows = statement.executeQuery()) {
                    last = rows.next() ? Optional.ofNullable(claimed(rows)) : Optional.empty();
                }
            }
            var reward = rewards.today(last, today);
            if (reward.claimed()) {
                return Optional.empty();
            }
            db.update(connection, "UPDATE bc_daily_rewards SET streak = ?, last_claim = ? WHERE player_uuid = ?",
                    reward.streak(), today, player.toString());
            if (reward.coins() > 0) {
                db.update(connection, "INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), COINS = COINS + VALUES(COINS)",
                        player.toString(), name, reward.coins());
            }
            return Optional.of(reward);
        });
    }

    private static DailyRewards.Claimed claimed(ResultSet row) throws SQLException {
        var day = row.getObject("last_claim", LocalDate.class);
        return day == null ? null : new DailyRewards.Claimed(row.getInt("streak"), day);
    }
}
