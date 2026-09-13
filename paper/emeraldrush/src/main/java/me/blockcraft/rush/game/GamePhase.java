package me.blockcraft.rush.game;

import me.blockcraft.common.network.ServerState;

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

    public ServerState serverState() {
        return switch (this) {
            case LOBBY -> ServerState.LOBBY;
            case SEARCH, SHOPPING, DEATHMATCH -> ServerState.INGAME;
            case ENDING -> ServerState.RESTART;
        };
    }
}
