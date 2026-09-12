package me.blockcraft.quicksg.storage;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

public final class StatsRepository {

    private final Database db;

    public StatsRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS qsg_stats (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), "
                + "KILLS INT NOT NULL DEFAULT 0, DEATHS INT NOT NULL DEFAULT 0, WIN INT NOT NULL DEFAULT 0, LOSE INT NOT NULL DEFAULT 0, "
                + "COINS INT NOT NULL DEFAULT 0)");
    }

    public Stats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE, COINS FROM qsg_stats WHERE UUID = ?",
                row -> new Stats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE"), row.getInt("COINS")),
                player.toString()).orElse(Stats.EMPTY);
    }

    public void add(UUID player, String name, StatColumn column, int delta) {
        String col = column.sql();
        db.update("INSERT INTO qsg_stats (UUID, NAME, " + col + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), " + col + " = " + col + " + ?",
                player.toString(), name, delta, delta);
    }

    public List<TopEntry> top(StatColumn column, int limit) {
        return db.queryList("SELECT NAME, " + column.sql() + " AS VALUE FROM qsg_stats ORDER BY " + column.sql() + " DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt("VALUE")), limit);
    }
}
