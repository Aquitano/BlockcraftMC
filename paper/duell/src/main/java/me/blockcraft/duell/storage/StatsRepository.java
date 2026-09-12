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
        db.execute("CREATE TABLE IF NOT EXISTS duell_stats (UUID VARCHAR(100), NAME VARCHAR(100), KILLS int, DEATHS int, WIN int, LOSE int, COINS int)");
    }

    public Stats get(UUID player) {
        return db.queryOne("SELECT KILLS, DEATHS, WIN, LOSE FROM duell_stats WHERE UUID = ?",
                row -> new Stats(row.getInt("KILLS"), row.getInt("DEATHS"), row.getInt("WIN"), row.getInt("LOSE")),
                player.toString()).orElse(Stats.EMPTY);
    }

    public void add(UUID player, String name, StatColumn column, int delta) {
        int updated = db.update("UPDATE duell_stats SET " + column.sql() + " = " + column.sql() + " + ? WHERE UUID = ?",
                delta, player.toString());
        if (updated == 0) {
            db.update("INSERT INTO duell_stats (UUID, NAME, KILLS, DEATHS, WIN, LOSE, COINS) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    player.toString(), name,
                    column == StatColumn.KILLS ? delta : 0,
                    column == StatColumn.DEATHS ? delta : 0,
                    column == StatColumn.WINS ? delta : 0,
                    column == StatColumn.LOSSES ? delta : 0,
                    INITIAL_COINS);
        }
    }

    public List<TopEntry> topWins(int limit) {
        return db.queryList("SELECT NAME, WIN FROM duell_stats ORDER BY WIN DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt("WIN")), limit);
    }
}
