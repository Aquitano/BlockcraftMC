package me.blockcraft.rush.storage;

public enum StatCategory {
    WINS("WIN", "wins"),
    DEATHS("DEATHS", "deaths"),
    KILLS("KILLS", "kills"),
    LOSES("LOSE", "loses"),
    POINTS("COINS", "points");

    private final String column;
    private final String key;

    StatCategory(String column, String key) {
        this.column = column;
        this.key = key;
    }

    String column() {
        return column;
    }

    public String key() {
        return key;
    }
}
