package me.blockcraft.common.shop;

import me.blockcraft.common.db.Database;

import java.util.UUID;

public final class UnlockRewards {

    private final Database db;

    public UnlockRewards(Database db) {
        this.db = db;
    }

    public boolean grant(UUID player, String name, String item, int coins) {
        return db.transaction(connection -> {
            try (var unlock = connection.prepareStatement("INSERT IGNORE INTO bc_unlocks (player_uuid, item) VALUES (?, ?)")) {
                unlock.setString(1, player.toString());
                unlock.setString(2, item);
                if (unlock.executeUpdate() == 0) {
                    return false;
                }
            }
            if (coins > 0) {
                try (var credit = connection.prepareStatement("INSERT INTO VCOINS (UUID, NAME, COINS) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), COINS = COINS + VALUES(COINS)")) {
                    credit.setString(1, player.toString());
                    credit.setString(2, name);
                    credit.setInt(3, coins);
                    credit.executeUpdate();
                }
            }
            return true;
        });
    }
}
