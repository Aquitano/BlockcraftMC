package me.blockcraft.quicksg.storage;

import java.util.Optional;

public enum StatColumn {
    KILLS("KILLS", "kills"),
    DEATHS("DEATHS", "deaths"),
    WINS("WIN", "wins"),
    LOSSES("LOSE", "loses"),
    POINTS("COINS", "points");

    private final String sql;
    private final String category;

    StatColumn(String sql, String category) {
        this.sql = sql;
        this.category = category;
    }

    public String sql() {
        return sql;
    }

    public String category() {
        return category;
    }

    public static Optional<StatColumn> parse(String category) {
        for (var column : values()) {
            if (column.category.equalsIgnoreCase(category)) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }
}
