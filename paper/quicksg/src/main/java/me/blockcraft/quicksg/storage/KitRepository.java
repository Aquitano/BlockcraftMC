package me.blockcraft.quicksg.storage;

import me.blockcraft.common.db.Database;
import me.blockcraft.quicksg.kit.Kit;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public final class KitRepository {

    private final Database db;

    public KitRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS qsg_kits (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), "
                + "KIT1 INT NOT NULL DEFAULT 0, KIT2 INT NOT NULL DEFAULT 0, KIT3 INT NOT NULL DEFAULT 0, KIT4 INT NOT NULL DEFAULT 0, "
                + "KIT5 INT NOT NULL DEFAULT 0)");
    }

    public Set<Kit> owned(UUID player) {
        return db.queryOne("SELECT KIT1, KIT2, KIT3, KIT4, KIT5 FROM qsg_kits WHERE UUID = ?", row -> {
            var owned = EnumSet.noneOf(Kit.class);
            for (var kit : Kit.values()) {
                if (row.getInt(kit.column()) == 1) {
                    owned.add(kit);
                }
            }
            return owned;
        }, player.toString()).orElseGet(() -> EnumSet.noneOf(Kit.class));
    }

    public boolean owns(UUID player, Kit kit) {
        return kit.free() || owned(player).contains(kit);
    }

    public void unlock(UUID player, String name, Kit kit) {
        db.update("INSERT INTO qsg_kits (UUID, NAME, " + kit.column() + ") VALUES (?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), " + kit.column() + " = 1", player.toString(), name);
    }
}
