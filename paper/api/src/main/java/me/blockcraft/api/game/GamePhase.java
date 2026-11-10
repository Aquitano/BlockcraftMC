package me.blockcraft.api.game;

import me.blockcraft.common.network.ServerState;

/** The three steps of every {@link Round} and what the proxy is told about them; a game may split INGAME further. */
public enum GamePhase {
    LOBBY(ServerState.LOBBY),
    INGAME(ServerState.INGAME),
    ENDING(ServerState.RESTART);

    private final ServerState serverState;

    GamePhase(ServerState serverState) {
        this.serverState = serverState;
    }

    public ServerState serverState() {
        return serverState;
    }
}
