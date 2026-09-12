package me.blockcraft.duell.storage;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.UUID;

public final class StatsRepository {

    private static final int INITIAL_COINS = 100;

    private final Database db;

    public StatsRepository(Database db) {
        this.db = db;
    }

    public void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS duell_stats (UUID VARCHAR(36) NOT NULL PRIMARY KEY, NAME VARCHAR(16), "
                + "KILLS INT NOT NULL DEFAULT 0, DEATHS INT NOT NULL DEFAULT 0, WIN INT NOT NULL DEFAULT 0, LOSE INT NOT NULL DEFAULT 0, "
                + "COINS INT NOT NULL DEFAULT " + INITIAL_COINS + ")");
    }

    public Stats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE FROM duell_stats WHERE UUID = ?",
                row -> new Stats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE")),
                player.toString()).orElse(Stats.EMPTY);
    }

    public void add(UUID player, String name, StatColumn column, int delta) {
        String col = column.sql();
        db.update("INSERT INTO duell_stats (UUID, NAME, " + col + ") VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), " + col + " = " + col + " + ?",
                player.toString(), name, delta, delta);
    }

    public List<TopEntry> topWins(int limit) {
        return db.queryList("SELECT NAME, WIN FROM duell_stats ORDER BY WIN DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt("WIN")), limit);
    }
}
