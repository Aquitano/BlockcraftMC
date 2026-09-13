package me.blockcraft.api.game;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

/** One legacy per-game stats table ({@code duell_stats}, {@code qsg_stats}, {@code em_stats}); they share a layout. */
public final class StatsRepository {

    private final Database db;
    private final String table;

    public StatsRepository(Database db, String table) {
        this.db = db;
        this.table = table;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS " + table + " (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), "
                + "KILLS INT NOT NULL DEFAULT 0, DEATHS INT NOT NULL DEFAULT 0, WIN INT NOT NULL DEFAULT 0, LOSE INT NOT NULL DEFAULT 0, "
                + "COINS INT NOT NULL DEFAULT 0)");
    }

    public Stats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE, COINS FROM " + table + " WHERE UUID = ?",
                row -> new Stats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE"), row.getInt("COINS")),
                player.toString()).orElse(Stats.EMPTY);
    }

    public void add(UUID player, String name, Stat stat, int delta) {
        String column = stat.column();
        db.update("INSERT INTO " + table + " (UUID, NAME, " + column + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), " + column + " = " + column + " + ?",
                player.toString(), name, delta, delta);
    }

    public List<TopEntry> top(Stat stat, int limit) {
        String column = stat.column();
        return db.queryList("SELECT NAME, " + column + " FROM " + table + " ORDER BY " + column + " DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt(column)), limit);
    }
}
