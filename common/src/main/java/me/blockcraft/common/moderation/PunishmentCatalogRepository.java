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

    /** All or nothing: a rejected entry leaves the previous catalogue in place. */
    public void replace(List<Entry> entries) {
        db.transaction(connection -> {
            try (var clear = connection.prepareStatement("DELETE FROM bc_punishment_catalog")) {
                clear.executeUpdate();
            }
            try (var insert = connection.prepareStatement("INSERT INTO bc_punishment_catalog (number, type, reason) VALUES (?, ?, ?)")) {
                for (var entry : entries) {
                    insert.setInt(1, entry.number());
                    insert.setString(2, entry.type().name());
                    insert.setString(3, entry.reason());
                    insert.executeUpdate();
                }
            }
            return null;
        });
    }

    public List<Entry> all() {
        return db.queryList("SELECT number, type, reason FROM bc_punishment_catalog ORDER BY number",
                row -> new Entry(row.getInt("number"), PunishmentType.valueOf(row.getString("type")), row.getString("reason")));
    }
}
