package me.blockcraft.rush.storage;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

public final class StatsRepository {

    private final Database db;

    public StatsRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS em_stats (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), "
                + "KILLS INT NOT NULL DEFAULT 0, DEATHS INT NOT NULL DEFAULT 0, WIN INT NOT NULL DEFAULT 0, LOSE INT NOT NULL DEFAULT 0, "
                + "COINS INT NOT NULL DEFAULT 0)");
    }

    public PlayerStats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE, COINS FROM em_stats WHERE UUID = ?",
                row -> new PlayerStats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE"), row.getInt("COINS")),
                player.toString()).orElse(PlayerStats.EMPTY);
    }

    public void increment(UUID player, String name, StatCategory category, int delta) {
        String column = category.column();
        db.update("INSERT INTO em_stats (UUID, NAME, " + column + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), " + column + " = " + column + " + ?",
                player.toString(), name, delta, delta);
    }

    public List<TopEntry> top(StatCategory category, int limit) {
        String column = category.column();
        return db.queryList("SELECT NAME, " + column + " FROM em_stats ORDER BY " + column + " DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt(column)), limit);
    }
}
