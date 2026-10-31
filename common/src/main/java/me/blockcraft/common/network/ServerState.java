package me.blockcraft.common.network;

/** What a backend is doing right now; only {@link #LOBBY} servers take new players. */
public enum ServerState {
    LOBBY,
    INGAME,
    RESTART
}
