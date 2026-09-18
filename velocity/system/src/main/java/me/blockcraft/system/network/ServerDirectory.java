package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** The live view of {@code bc_servers} as last read by {@link ServerDiscovery}, and how a group is joined. */
public final class ServerDirectory {

    private static final Comparator<ServerRecord> BY_ONLINE = Comparator.comparingInt(server -> server.status().online());

    private final ProxyServer proxy;
    private final String lobbyGroup;
    private volatile Map<String, ServerRecord> servers = Map.of();

    public ServerDirectory(ProxyServer proxy, String lobbyGroup) {
        this.proxy = proxy;
        this.lobbyGroup = lobbyGroup;
    }

    void update(List<ServerRecord> alive) {
        servers = alive.stream().collect(Collectors.toUnmodifiableMap(ServerRecord::name, Function.identity()));
    }

    public Optional<ServerRecord> get(String server) {
        return Optional.ofNullable(servers.get(server));
    }

    public Optional<String> groupOf(String server) {
        return get(server).map(ServerRecord::group);
    }

    /** Whether a named server still takes players, which is what every join path asks before it connects one. */
    public boolean isJoinable(String server) {
        return get(server).map(record -> record.status().joinable()).orElse(false);
    }

    /** Whether a round is running there, which nobody joins but a friend may watch; a lobby is never watched. */
    public boolean isWatchable(String server) {
        return get(server)
                .map(record -> record.status().state() == ServerState.INGAME && !record.group().equals(lobbyGroup))
                .orElse(false);
    }

    public Set<String> groups() {
        return servers.values().stream().map(ServerRecord::group).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Games fill up one instance before the next: the joinable server with the most players. */
    public Optional<RegisteredServer> fill(String group) {
        return joinable(group).max(BY_ONLINE).flatMap(this::registered);
    }

    /** Lobbies spread the load: the joinable server with the fewest players. */
    public Optional<RegisteredServer> balance(String group) {
        return balance(group, Set.of());
    }

    public Optional<RegisteredServer> balance(String group, Set<String> excludedServers) {
        return joinable(group).filter(server -> !excludedServers.contains(server.name())).min(BY_ONLINE).flatMap(this::registered);
    }

    private Stream<ServerRecord> joinable(String group) {
        return servers.values().stream().filter(server -> server.group().equals(group) && server.status().joinable());
    }

    private Optional<RegisteredServer> registered(ServerRecord server) {
        return proxy.getServer(server.name());
    }
}
