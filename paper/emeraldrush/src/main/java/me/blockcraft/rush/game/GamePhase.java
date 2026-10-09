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

    /** The shared round's step this phase belongs to; the three phases of play are all one running round. */
    public me.blockcraft.api.game.GamePhase roundPhase() {
        return switch (this) {
            case LOBBY -> me.blockcraft.api.game.GamePhase.LOBBY;
            case SEARCH, SHOPPING, DEATHMATCH -> me.blockcraft.api.game.GamePhase.INGAME;
            case ENDING -> me.blockcraft.api.game.GamePhase.ENDING;
        };
    }
}
