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
        db.execute("CREATE TABLE IF NOT EXISTS em_kits (UUID VARCHAR(100), NAME VARCHAR(16), KIT1 int, KIT2 int, KIT3 int, KIT4 int, "
                + "KIT5 int, KIT6 int, KIT7 int, KIT8 int, KIT9 int, KIT10 int, KIT11 int, KIT12 int, KIT13 int, KIT14 int, KIT15 int, KIT16 int)");
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
        int updated = db.update("UPDATE em_kits SET " + perk.column() + " = 1, NAME = ? WHERE UUID = ?", name, player.toString());
        if (updated == 0) {
            db.update("INSERT INTO em_kits (UUID, NAME, KIT1, KIT2, KIT3, KIT4, KIT5, KIT6, KIT7, KIT8) VALUES (?, ?, 0, 0, 0, 0, 0, 0, 0, 0)",
                    player.toString(), name);
            db.update("UPDATE em_kits SET " + perk.column() + " = 1 WHERE UUID = ?", player.toString());
        }
    }
}
