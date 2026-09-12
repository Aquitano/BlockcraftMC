package me.blockcraft.signs.sign;

import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** All live servers of a group folded into what one sign can show. */
public record GroupStatus(Optional<ServerState> state, int online, int maxPlayers, boolean joinable) {

    public static final GroupStatus OFFLINE = new GroupStatus(Optional.empty(), 0, 0, false);

    /** A group waiting for players shows as lobby even while other instances play, so the sign invites clicks. */
    public static GroupStatus of(List<ServerRecord> servers) {
        if (servers.isEmpty()) {
            return OFFLINE;
        }
        boolean joinable = servers.stream().anyMatch(server -> server.status().joinable());
        var state = joinable
                ? ServerState.LOBBY
                : servers.stream().map(server -> server.status().state()).min(Comparator.naturalOrder()).orElseThrow();
        return new GroupStatus(Optional.of(state),
                servers.stream().mapToInt(server -> server.status().online()).sum(),
                servers.stream().mapToInt(server -> server.status().maxPlayers()).sum(),
                joinable);
    }
}
