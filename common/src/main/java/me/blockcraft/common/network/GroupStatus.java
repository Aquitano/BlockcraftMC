package me.blockcraft.common.network;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Availability and total population shared by game cards and status signs. {@code onDemand} is the group's state
 * when the proxy starts its server on demand, and tells a group that sleeps or is starting from one that is down.
 */
public record GroupStatus(Optional<ServerState> state, int online, int maxPlayers, boolean joinable,
                          Optional<GroupState> onDemand) {

    public enum Availability {
        AVAILABLE, FULL, INGAME, RESTART, SLEEPING, STARTING, OFFLINE
    }

    public Availability availability() {
        if (joinable) {
            return Availability.AVAILABLE;
        }
        return state.map(value -> switch (value) {
            case LOBBY -> Availability.FULL;
            case INGAME -> Availability.INGAME;
            case RESTART -> Availability.RESTART;
        }).orElseGet(() -> onDemand.map(group -> switch (group) {
            case ASLEEP, DRAINING -> Availability.SLEEPING;
            case STARTING -> Availability.STARTING;
            case AWAKE -> Availability.OFFLINE;
        }).orElse(Availability.OFFLINE));
    }

    /** Whether a click gets the player into a round, now or once the server the click wakes is up. */
    public boolean clickable() {
        return switch (availability()) {
            case AVAILABLE, SLEEPING, STARTING -> true;
            default -> false;
        };
    }

    public GroupStatus withOnDemand(GroupState group) {
        return new GroupStatus(state, online, maxPlayers, joinable, Optional.of(group));
    }

    public static final GroupStatus OFFLINE = new GroupStatus(Optional.empty(), 0, 0, false, Optional.empty());

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
                joinable, Optional.empty());
    }
}
