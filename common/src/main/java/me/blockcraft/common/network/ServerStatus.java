package me.blockcraft.common.network;

/** {@code tps} is the one-minute average, {@code mspt} the average milliseconds a tick took. */
public record ServerStatus(ServerState state, int online, int maxPlayers, double tps, double mspt) {

    public boolean joinable() {
        return state == ServerState.LOBBY && online < maxPlayers;
    }
}
