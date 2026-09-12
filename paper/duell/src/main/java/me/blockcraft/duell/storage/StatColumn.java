package me.blockcraft.duell.storage;

public enum StatColumn {
    KILLS("KILLS"),
    DEATHS("DEATHS"),
    WINS("WIN"),
    LOSSES("LOSE");

    private final String sql;

    StatColumn(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
