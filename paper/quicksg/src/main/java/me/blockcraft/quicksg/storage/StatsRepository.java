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
        db.execute("CREATE TABLE IF NOT EXISTS qsg_stats (UUID VARCHAR(100), NAME VARCHAR(16), KILLS int, DEATHS int, WIN int, LOSE int, COINS int)");
    }

    public Stats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE, COINS FROM qsg_stats WHERE UUID = ?",
                row -> new Stats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE"), row.getInt("COINS")),
                player.toString()).orElse(Stats.EMPTY);
    }

    public void add(UUID player, String name, StatColumn column, int delta) {
        int updated = db.update("UPDATE qsg_stats SET " + column.sql() + " = " + column.sql() + " + ?, NAME = ? WHERE UUID = ?",
                delta, name, player.toString());
        if (updated == 0) {
            db.update("INSERT INTO qsg_stats (UUID, NAME, KILLS, DEATHS, WIN, LOSE, COINS) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    player.toString(), name,
                    column == StatColumn.KILLS ? delta : 0,
                    column == StatColumn.DEATHS ? delta : 0,
                    column == StatColumn.WINS ? delta : 0,
                    column == StatColumn.LOSSES ? delta : 0,
                    column == StatColumn.POINTS ? delta : 0);
        }
    }

    public List<TopEntry> top(StatColumn column, int limit) {
        return db.queryList("SELECT NAME, " + column.sql() + " AS VALUE FROM qsg_stats ORDER BY " + column.sql() + " DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt("VALUE")), limit);
    }
}
