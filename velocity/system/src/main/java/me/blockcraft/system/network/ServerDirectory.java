package me.blockcraft.system.network;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * The live view of {@code bc_servers} as last read by {@link ServerDiscovery}, and how a group is joined. Players the
 * proxy sends to a game hold a seat there until they arrive, their connection fails or the seat times out, so a burst
 * of joins does not overfill an instance whose heartbeat has not caught up yet.
 */
public final class ServerDirectory {

    private record Seat(String server, Instant expires) {
    }

    private final ProxyServer proxy;
    private final String lobbyGroup;
    private final Duration seatTimeout;
    private final InstantSource clock;
    private final Map<UUID, Seat> seats = new HashMap<>();
    private volatile Map<String, ServerRecord> servers = Map.of();

    public ServerDirectory(ProxyServer proxy, String lobbyGroup, Duration seatTimeout) {
        this(proxy, lobbyGroup, seatTimeout, InstantSource.system());
    }

    ServerDirectory(ProxyServer proxy, String lobbyGroup, Duration seatTimeout, InstantSource clock) {
        this.proxy = proxy;
        this.lobbyGroup = lobbyGroup;
        this.seatTimeout = seatTimeout;
        this.clock = clock;
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

    /** Whether a named server still has a free seat, which is what every join path asks before it connects one. */
    public synchronized boolean isJoinable(String server) {
        expireSeats();
        return get(server).map(record -> hasRoom(record, 1)).orElse(false);
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

    /** Games fill up one instance before the next: the fullest server with room for all the players, who get a seat each. */
    public synchronized Optional<RegisteredServer> fill(String group, Collection<UUID> players) {
        expireSeats();
        var target = joinable(group, players.size()).max(Comparator.comparingInt(this::occupancy)).flatMap(this::registered);
        target.ifPresent(server -> players.forEach(player -> seat(player, server.getServerInfo().getName())));
        return target;
    }

    /**
     * Whether the player may follow someone onto that server: into the seat held for them, into a free one, or as a
     * spectator of a running round. Lobbies and servers outside {@code bc_servers} take everyone, as they always did.
     */
    public synchronized boolean admit(UUID player, String server) {
        var record = get(server);
        if (record.isEmpty() || record.get().group().equals(lobbyGroup) || isWatchable(server)) {
            return true;
        }
        expireSeats();
        var held = seats.get(player);
        if (held != null && held.server().equals(server)) {
            return true;
        }
        if (!hasRoom(record.get(), 1)) {
            return false;
        }
        seat(player, server);
        return true;
    }

    /** Gives up the seat the player holds on that server, if any; a seat elsewhere belongs to a newer attempt. */
    public synchronized void release(UUID player, String server) {
        seats.computeIfPresent(player, (id, seat) -> seat.server().equals(server) ? null : seat);
    }

    /** Connects the player with Velocity's own error messages and releases their seat there unless they arrive. */
    public CompletableFuture<Boolean> send(Player player, RegisteredServer server) {
        return player.createConnectionRequest(server).connectWithIndication().handle((connected, error) -> {
            boolean arrived = error == null && connected;
            if (!arrived) {
                release(player.getUniqueId(), server.getServerInfo().getName());
            }
            return arrived;
        });
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        release(event.getPlayer().getUniqueId(), event.getServer().getServerInfo().getName());
    }

    @Subscribe
    public synchronized void onDisconnect(DisconnectEvent event) {
        seats.remove(event.getPlayer().getUniqueId());
    }

    /** Lobbies spread the load: the joinable server with the fewest players. */
    public Optional<RegisteredServer> balance(String group) {
        return balance(group, Set.of());
    }

    public synchronized Optional<RegisteredServer> balance(String group, Set<String> excludedServers) {
        expireSeats();
        return joinable(group, 1).filter(server -> !excludedServers.contains(server.name()))
                .min(Comparator.comparingInt(this::occupancy)).flatMap(this::registered);
    }

    private Stream<ServerRecord> joinable(String group, int players) {
        return servers.values().stream().filter(server -> server.group().equals(group) && hasRoom(server, players));
    }

    private boolean hasRoom(ServerRecord server, int players) {
        return server.status().state() == ServerState.LOBBY && occupancy(server) + players <= server.status().maxPlayers();
    }

    /**
     * The heartbeat can be seconds old, while the proxy knows exactly whom it connected there; a seat counts until its
     * player shows up among those.
     */
    private int occupancy(ServerRecord server) {
        var arrived = registered(server).map(RegisteredServer::getPlayersConnected).orElse(List.of()).stream()
                .map(Player::getUniqueId).collect(Collectors.toSet());
        long onTheirWay = seats.entrySet().stream()
                .filter(seat -> seat.getValue().server().equals(server.name()) && !arrived.contains(seat.getKey()))
                .count();
        return Math.max(server.status().online(), arrived.size()) + (int) onTheirWay;
    }

    private void seat(UUID player, String server) {
        seats.put(player, new Seat(server, clock.instant().plus(seatTimeout)));
    }

    private void expireSeats() {
        var now = clock.instant();
        seats.values().removeIf(seat -> !seat.expires().isAfter(now));
    }

    private Optional<RegisteredServer> registered(ServerRecord server) {
        return proxy.getServer(server.name());
    }
}
