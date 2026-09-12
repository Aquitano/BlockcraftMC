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
        db.execute("CREATE TABLE IF NOT EXISTS em_stats (UUID VARCHAR(100), NAME VARCHAR(16), KILLS int, DEATHS int, WIN int, LOSE int, COINS int)");
    }

    public PlayerStats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE, COINS FROM em_stats WHERE UUID = ?",
                row -> new PlayerStats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE"), row.getInt("COINS")),
                player.toString()).orElse(PlayerStats.EMPTY);
    }

    public void increment(UUID player, String name, StatCategory category, int delta) {
        String column = category.column();
        int updated = db.update("UPDATE em_stats SET " + column + " = " + column + " + ?, NAME = ? WHERE UUID = ?",
                delta, name, player.toString());
        if (updated == 0) {
            db.update("INSERT INTO em_stats (UUID, NAME, KILLS, DEATHS, WIN, LOSE, COINS) VALUES (?, ?, 0, 0, 0, 0, 0)",
                    player.toString(), name);
            db.update("UPDATE em_stats SET " + column + " = " + column + " + ? WHERE UUID = ?", delta, player.toString());
        }
    }

    public List<TopEntry> top(StatCategory category, int limit) {
        String column = category.column();
        return db.queryList("SELECT NAME, " + column + " FROM em_stats ORDER BY " + column + " DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt(column)), limit);
    }
}
