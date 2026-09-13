package me.blockcraft.api.game;

import java.util.Optional;

/** The columns every legacy {@code *_stats} table has, keyed the way /top and the menus name them. */
public enum Stat {
    KILLS("KILLS", "kills"),
    DEATHS("DEATHS", "deaths"),
    WINS("WIN", "wins"),
    LOSSES("LOSE", "loses"),
    POINTS("COINS", "points");

    private final String column;
    private final String key;

    Stat(String column, String key) {
        this.column = column;
        this.key = key;
    }

    public String column() {
        return column;
    }

    public String key() {
        return key;
    }

    public static Optional<Stat> parse(String key) {
        for (var stat : values()) {
            if (stat.key.equalsIgnoreCase(key)) {
                return Optional.of(stat);
            }
        }
        return Optional.empty();
    }
}
