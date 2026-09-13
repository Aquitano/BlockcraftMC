package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

import java.util.List;

/**
 * The /strafe catalogue as the proxy's config.yml defines it, mirrored into {@code bc_punishment_catalog} at every
 * proxy start so Paper menus can offer the same numbered punishments without a copy of the file.
 */
public final class PunishmentCatalogRepository {

    public record Entry(int number, PunishmentType type, String reason) {
    }

    private final Database db;

    public PunishmentCatalogRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_punishment_catalog ("
                + "number INT NOT NULL PRIMARY KEY, "
                + "type VARCHAR(8) NOT NULL, "
                + "reason VARCHAR(255) NOT NULL)");
    }

    public void replace(List<Entry> entries) {
        db.update("DELETE FROM bc_punishment_catalog");
        for (var entry : entries) {
            db.update("INSERT INTO bc_punishment_catalog (number, type, reason) VALUES (?, ?, ?)",
                    entry.number(), entry.type().name(), entry.reason());
        }
    }

    public List<Entry> all() {
        return db.queryList("SELECT number, type, reason FROM bc_punishment_catalog ORDER BY number",
                row -> new Entry(row.getInt("number"), PunishmentType.valueOf(row.getString("type")), row.getString("reason")));
    }
}
