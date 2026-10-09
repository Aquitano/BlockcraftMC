package me.blockcraft.common.ignore;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

/**
 * {@code bc_ignores}: one row per player and account they ignore, with the name the ignored player went by at the
 * time, so the list never shows the account behind a nick. The proxy writes it; the Paper servers read it at join.
 */
public final class IgnoreRepository {

    public record Ignored(UUID uuid, String name) {
    }

    private final Database db;

    public IgnoreRepository(Database db) {
        this.db = db;
    }

    public List<Ignored> list(UUID player) {
        return db.queryList("SELECT ignored_uuid, ignored_name FROM bc_ignores WHERE player_uuid = ? ORDER BY created_at",
                row -> new Ignored(UUID.fromString(row.getString("ignored_uuid")), row.getString("ignored_name")),
                player.toString());
    }

    public boolean ignores(UUID player, UUID other) {
        return db.queryOne("SELECT 1 FROM bc_ignores WHERE player_uuid = ? AND ignored_uuid = ?",
                row -> true, player.toString(), other.toString()).isPresent();
    }

    /** False when the account was already ignored. */
    public boolean add(UUID player, Ignored ignored) {
        return db.update("INSERT IGNORE INTO bc_ignores (player_uuid, ignored_uuid, ignored_name) VALUES (?, ?, ?)",
                player.toString(), ignored.uuid().toString(), ignored.name()) > 0;
    }

    public boolean remove(UUID player, UUID ignored) {
        return db.update("DELETE FROM bc_ignores WHERE player_uuid = ? AND ignored_uuid = ?",
                player.toString(), ignored.toString()) > 0;
    }
}
