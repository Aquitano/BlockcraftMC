package me.blockcraft.api.game;

import me.blockcraft.common.network.ServerState;

/** The three-step round of Duell and QuickSG and what the proxy is told about it. */
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
