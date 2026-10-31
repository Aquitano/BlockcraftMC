package me.blockcraft.common.network;

/**
 * One live row of {@code bc_servers}: what the backend reported in its heartbeat, and how many players the proxy last
 * counted there, those it is still sending included. Either count can be seconds behind the other.
 */
public record ServerRecord(ServerIdentity identity, ServerStatus status, int occupancy) {

    public String name() {
        return identity.name();
    }

    public String group() {
        return identity.group();
    }

    public int online() {
        return Math.max(status.online(), occupancy);
    }

    public boolean joinable() {
        return status.state() == ServerState.LOBBY && online() < status.maxPlayers();
    }
}
