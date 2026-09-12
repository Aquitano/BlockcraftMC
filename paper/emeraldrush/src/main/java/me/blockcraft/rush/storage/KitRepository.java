package me.blockcraft.rush.storage;

import me.blockcraft.common.db.Database;
import me.blockcraft.rush.kit.Perk;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public final class KitRepository {

    private final Database db;

    public KitRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS em_kits (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), "
                + "KIT1 INT NOT NULL DEFAULT 0, KIT3 INT NOT NULL DEFAULT 0, KIT4 INT NOT NULL DEFAULT 0)");
    }

    public Set<Perk> owned(UUID player) {
        return db.queryOne("SELECT KIT1, KIT3, KIT4 FROM em_kits WHERE UUID = ?", row -> {
            var owned = EnumSet.noneOf(Perk.class);
            for (var perk : Perk.values()) {
                if (row.getInt(perk.column()) == 1) {
                    owned.add(perk);
                }
            }
            return owned;
        }, player.toString()).orElseGet(() -> EnumSet.noneOf(Perk.class));
    }

    public boolean owns(UUID player, Perk perk) {
        return owned(player).contains(perk);
    }

    public void grant(UUID player, String name, Perk perk) {
        db.update("INSERT INTO em_kits (UUID, NAME, " + perk.column() + ") VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), " + perk.column() + " = 1", player.toString(), name);
    }
}
