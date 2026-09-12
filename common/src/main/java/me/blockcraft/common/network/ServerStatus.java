package me.blockcraft.common.network;

public record ServerStatus(ServerState state, int online, int maxPlayers) {

    public boolean joinable() {
        return state == ServerState.LOBBY && online < maxPlayers;
    }
}
