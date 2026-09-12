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
        db.execute("CREATE TABLE IF NOT EXISTS qsg_kits (UUID VARCHAR(100), NAME VARCHAR(16), KIT1 int, KIT2 int, KIT3 int, KIT4 int, KIT5 int, KIT6 int, KIT7 int, KIT8 int)");
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
        int updated = db.update("UPDATE qsg_kits SET " + kit.column() + " = 1, NAME = ? WHERE UUID = ?", name, player.toString());
        if (updated == 0) {
            db.update("INSERT INTO qsg_kits (UUID, NAME, KIT1, KIT2, KIT3, KIT4, KIT5, KIT6, KIT7, KIT8) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0)",
                    player.toString(), name,
                    kit == Kit.STARTER ? 1 : 0,
                    kit == Kit.BOW ? 1 : 0,
                    kit == Kit.HEARTS ? 1 : 0,
                    kit == Kit.CHAIN ? 1 : 0,
                    kit == Kit.DIAMOND ? 1 : 0);
        }
    }
}
