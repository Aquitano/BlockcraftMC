package me.blockcraft.common.network;

/** What a backend is doing right now; waiting rooms and continuous games take new players. */
public enum ServerState {
    LOBBY,
    OPEN,
    INGAME,
    RESTART;

    public boolean acceptsPlayers() {
        return this == LOBBY || this == OPEN;
    }
}
