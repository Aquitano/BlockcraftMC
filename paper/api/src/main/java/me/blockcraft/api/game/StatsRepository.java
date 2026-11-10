package me.blockcraft.api.game;

import me.blockcraft.common.db.Database;

import java.util.List;
import java.util.Optional;
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

    /** Adds a whole round's numbers to the player's row in one statement. */
    public void add(UUID player, String name, Stats delta) {
        db.update("INSERT INTO " + table + " (UUID, NAME, KILLS, DEATHS, WIN, LOSE, COINS) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE NAME = VALUES(NAME), KILLS = KILLS + VALUES(KILLS), DEATHS = DEATHS + VALUES(DEATHS), "
                + "WIN = WIN + VALUES(WIN), LOSE = LOSE + VALUES(LOSE), COINS = COINS + VALUES(COINS)",
                player.toString(), name, delta.kills(), delta.deaths(), delta.wins(), delta.losses(), delta.points());
    }

    public List<TopEntry> top(Stat stat, int limit) {
        String column = stat.column();
        return db.queryList("SELECT NAME, " + column + " FROM " + table + " ORDER BY " + column + " DESC LIMIT ?",
                row -> new TopEntry(row.getString("NAME"), row.getInt(column)), limit);
    }

    /** The top of the board with each row's player, for a face or a skin next to the name. */
    public List<Leader> leaders(Stat stat, int limit) {
        String column = stat.column();
        return db.queryList("SELECT UUID, NAME, " + column + " FROM " + table + " ORDER BY " + column + " DESC LIMIT ?",
                row -> new Leader(uuid(row.getString("UUID")), String.valueOf(row.getString("NAME")), row.getInt(column)), limit);
    }

    /** Where {@code player} stands for {@code stat}, ties sharing the better place; empty without a row. */
    public Optional<Placing> placing(UUID player, Stat stat) {
        String column = stat.column();
        return db.queryOne("SELECT " + column + " AS value, (SELECT COUNT(*) FROM " + table + " other WHERE other." + column
                        + " > own." + column + ") + 1 AS place FROM " + table + " own WHERE own.UUID = ?",
                row -> new Placing(row.getInt("place"), row.getInt("value")), player.toString());
    }

    private static Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
