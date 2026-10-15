package me.blockcraft.common.network;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Availability and total population shared by game cards and status signs. */
public record GroupStatus(Optional<ServerState> state, int online, int maxPlayers, boolean joinable) {

    public enum Availability {
        AVAILABLE, FULL, INGAME, RESTART, OFFLINE
    }

    public Availability availability() {
        if (joinable) {
            return Availability.AVAILABLE;
        }
        return state.map(value -> switch (value) {
            case LOBBY -> Availability.FULL;
            case INGAME -> Availability.INGAME;
            case RESTART -> Availability.RESTART;
        }).orElse(Availability.OFFLINE);
    }

    public static final GroupStatus OFFLINE = new GroupStatus(Optional.empty(), 0, 0, false);

    /** A group waiting for players shows as lobby even while other instances play, so the sign invites clicks. */
    public static GroupStatus of(List<ServerRecord> servers) {
        if (servers.isEmpty()) {
            return OFFLINE;
        }
        boolean joinable = servers.stream().anyMatch(ServerRecord::joinable);
        var state = joinable
                ? ServerState.LOBBY
                : servers.stream().map(server -> server.status().state()).min(Comparator.naturalOrder()).orElseThrow();
        return new GroupStatus(Optional.of(state),
                servers.stream().mapToInt(ServerRecord::online).sum(),
                servers.stream().mapToInt(server -> server.status().maxPlayers()).sum(),
                joinable);
    }
}
