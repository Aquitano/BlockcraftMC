package me.blockcraft.rush.game;

public enum GamePhase {
    LOBBY,
    SEARCH,
    SHOPPING,
    DEATHMATCH,
    ENDING;

    public boolean allowsDamage() {
        return this == DEATHMATCH || this == ENDING;
    }

    public boolean locksInventory() {
        return this == LOBBY || this == SEARCH;
    }
}
