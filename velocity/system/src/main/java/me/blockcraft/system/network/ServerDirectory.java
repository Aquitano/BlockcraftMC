package me.blockcraft.system.network;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.system.LiveSession;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * The live view of {@code bc_servers} as last read by {@link ServerDiscovery}, and how a group is joined. Players the
 * proxy sends to a game hold a seat there until they arrive, their connection fails or the seat times out, so a burst
 * of joins does not overfill an instance whose heartbeat has not caught up yet. Every change in what it counts goes
 * back to {@code bc_servers}, so status signs and the navigator see the seats as well.
 */
public final class ServerDirectory {

    /** How a player may go to one particular server: into a seat, as a spectator of its running round, or not at all. */
    public enum Entry { SEATED, WATCHING, CLOSED }

    private record Seat(String server, Instant expires) {
    }

    private final ProxyServer proxy;
    private final String lobbyGroup;
    private final Duration seatTimeout;
    private final InstantSource clock;
    private final Consumer<Map<String, Integer>> publish;
    private final Map<UUID, Seat> seats = new HashMap<>();
    private final Map<String, Integer> published = new HashMap<>();
    private volatile Map<String, ServerRecord> servers = Map.of();

    /** {@code publish} receives the counts that changed and must not block, since it is called while seats change. */
    public ServerDirectory(ProxyServer proxy, String lobbyGroup, Duration seatTimeout, Consumer<Map<String, Integer>> publish) {
        this(proxy, lobbyGroup, seatTimeout, InstantSource.system(), publish);
    }

    ServerDirectory(ProxyServer proxy, String lobbyGroup, Duration seatTimeout, InstantSource clock,
                    Consumer<Map<String, Integer>> publish) {
        this.proxy = proxy;
        this.lobbyGroup = lobbyGroup;
        this.seatTimeout = seatTimeout;
        this.clock = clock;
        this.publish = publish;
    }

    synchronized void update(List<ServerRecord> alive) {
        servers = alive.stream().collect(Collectors.toUnmodifiableMap(ServerRecord::name, Function.identity()));
        // Every poll writes all counts again, which also mends a write that arrived out of order.
        published.clear();
        expireSeats();
        publishChanges();
    }

    public Optional<ServerRecord> get(String server) {
        return Optional.ofNullable(servers.get(server));
    }

    public Optional<String> groupOf(String server) {
        return get(server).map(ServerRecord::group);
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
        target.ifPresent(server -> {
            players.forEach(player -> seat(player, server.getServerInfo().getName()));
            publishChanges();
        });
        return target;
    }

    /**
     * Seats the player on a server they chose by name, such as a friend's or one a JoinMe named, keeping the seat
     * they already hold there; a running round they may only watch, and a full or restarting server stays closed.
     */
    public synchronized Entry enter(UUID player, String server) {
        var record = get(server);
        if (record.isEmpty()) {
            return Entry.CLOSED;
        }
        if (isWatchable(server)) {
            return Entry.WATCHING;
        }
        expireSeats();
        var held = seats.get(player);
        if (held != null && held.server().equals(server)) {
            return Entry.SEATED;
        }
        if (!hasRoom(record.get(), 1)) {
            return Entry.CLOSED;
        }
        seat(player, server);
        publishChanges();
        return Entry.SEATED;
    }

    /**
     * Whether the player may follow someone onto that server, as {@link #enter} decides. Lobbies and servers outside
     * {@code bc_servers} take everyone, as they always did.
     */
    public synchronized boolean admit(UUID player, String server) {
        var record = get(server);
        return record.isEmpty() || record.get().group().equals(lobbyGroup) || enter(player, server) != Entry.CLOSED;
    }

    /** Gives up the seat the player holds on that server, if any; a seat elsewhere belongs to a newer attempt. */
    public synchronized void release(UUID player, String server) {
        if (unseat(player, server)) {
            publishChanges();
        }
    }

    private boolean unseat(UUID player, String server) {
        var held = seats.get(player);
        return held != null && held.server().equals(server) && seats.remove(player, held);
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

    /**
     * An arrival turns a seat into a connected player and changes no count, so nothing is published. Only after the
     * switch does the server list the player among its connected ones, so the seat holds until then.
     */
    @Subscribe
    public synchronized void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        player.getCurrentServer().ifPresent(connection -> unseat(player.getUniqueId(), connection.getServerInfo().getName()));
    }

    @Subscribe
    public synchronized void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event) && seats.remove(event.getPlayer().getUniqueId()) != null) {
            publishChanges();
        }
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
     * player shows up among those. The row's occupancy column is left out, since it is this count as published before.
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
        if (seats.values().removeIf(seat -> !seat.expires().isAfter(now))) {
            publishChanges();
        }
    }

    private void publishChanges() {
        var changed = new HashMap<String, Integer>();
        for (var server : servers.values()) {
            int count = occupancy(server);
            if (!Objects.equals(published.put(server.name(), count), count)) {
                changed.put(server.name(), count);
            }
        }
        published.keySet().retainAll(servers.keySet());
        if (!changed.isEmpty()) {
            publish.accept(Map.copyOf(changed));
        }
    }

    private Optional<RegisteredServer> registered(ServerRecord server) {
        return proxy.getServer(server.name());
    }
}
