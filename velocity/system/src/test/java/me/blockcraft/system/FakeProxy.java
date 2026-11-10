package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.Scheduler;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Just enough of the Velocity API for the unit tests: players with a name, id and permissions on a proxy that lists
 * them, backends a player can be connected to, and the messages each side received.
 */
public final class FakeProxy {

    private final Map<UUID, Player> online = new LinkedHashMap<>();
    private final Map<UUID, ServerConnection> connections = new LinkedHashMap<>();
    private final Map<UUID, List<Object>> received = new LinkedHashMap<>();
    private final Map<String, RegisteredServer> servers = new LinkedHashMap<>();
    private final Map<UUID, List<RegisteredServer>> requested = new LinkedHashMap<>();
    private final Map<UUID, CompletableFuture<Boolean>> nextConnections = new LinkedHashMap<>();
    private final List<Runnable> scheduled = new ArrayList<>();

    private final Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
            new Class<?>[]{Scheduler.class}, (proxy, method, args) -> switch (method.getName()) {
                case "buildTask" -> Proxy.newProxyInstance(Scheduler.TaskBuilder.class.getClassLoader(),
                        new Class<?>[]{Scheduler.TaskBuilder.class}, (builder, action, values) -> switch (action.getName()) {
                            case "delay" -> builder;
                            case "schedule" -> {
                                scheduled.add((Runnable) args[1]);
                                yield null;
                            }
                            default -> throw new AssertionError(action.getName());
                        });
                default -> throw new AssertionError(method.getName());
            });

    final ChannelRegistrar registrar = (ChannelRegistrar) Proxy.newProxyInstance(ChannelRegistrar.class.getClassLoader(),
            new Class<?>[]{ChannelRegistrar.class}, (proxy, method, args) -> switch (method.getName()) {
                case "register", "unregister" -> null;
                default -> throw new AssertionError(method.getName());
            });

    public final ProxyServer server = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
            new Class<?>[]{ProxyServer.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getAllPlayers" -> List.copyOf(online.values());
                case "getPlayer" -> args[0] instanceof UUID id ? Optional.ofNullable(online.get(id)) : byName(String.valueOf(args[0]));
                case "getChannelRegistrar" -> registrar;
                case "getServer" -> Optional.ofNullable(servers.get(args[0]));
                case "getScheduler" -> scheduler;
                default -> throw new AssertionError(method.getName());
            });

    public Player join(String name, String... permissions) {
        var id = UUID.randomUUID();
        var granted = Set.of(permissions);
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getUsername", "toString" -> name;
                    case "hasPermission" -> granted.contains(args[0]);
                    case "isActive" -> online.containsKey(id);
                    case "getCurrentServer" -> Optional.ofNullable(connections.get(id));
                    case "createConnectionRequest" -> request(id, (RegisteredServer) args[0]);
                    case "getAppliedResourcePacks" -> List.of();
                    case "playSound" -> null;
                    case "sendPlayerListHeaderAndFooter" -> received.computeIfAbsent(id, key -> new ArrayList<>()).add(args[0]);
                    case "sendMessage" -> received.computeIfAbsent(id, key -> new ArrayList<>()).add(args[0]);
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method.getName());
                });
        online.put(id, player);
        return player;
    }

    void leave(Player player) {
        online.remove(player.getUniqueId());
        connections.remove(player.getUniqueId());
    }

    /** Moves the player onto a backend; the connection records every plugin message the proxy sends to that server. */
    public ServerConnection connect(Player player, String serverName) {
        var info = new ServerInfo(serverName, new InetSocketAddress("127.0.0.1", 25566));
        var registered = servers.computeIfAbsent(serverName, name -> (RegisteredServer) Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(), new Class<?>[]{RegisteredServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "getPlayersConnected" -> connections.values().stream()
                            .filter(connection -> connection.getServerInfo().getName().equals(name))
                            .map(ServerConnection::getPlayer).toList();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> info.hashCode();
                    default -> throw new AssertionError(method.getName());
                }));
        var sent = Collections.synchronizedList(new ArrayList<byte[]>());
        var connection = (ServerConnection) Proxy.newProxyInstance(ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class, Sent.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> player;
                    case "getServerInfo" -> info;
                    case "getServer" -> registered;
                    case "sendPluginMessage" -> sent.add((byte[]) args[1]);
                    case "sent" -> List.copyOf(sent);
                    case "hashCode" -> info.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method.getName());
                });
        connections.put(player.getUniqueId(), connection);
        return connection;
    }

    public List<byte[]> sentTo(ServerConnection connection) {
        return ((Sent) connection).sent();
    }

    List<Object> received(Player player) {
        return received.getOrDefault(player.getUniqueId(), List.of());
    }

    public List<RegisteredServer> requested(Player player) {
        return requested.getOrDefault(player.getUniqueId(), List.of());
    }

    public void runScheduled() {
        var tasks = List.copyOf(scheduled);
        scheduled.clear();
        tasks.forEach(Runnable::run);
    }

    public CompletableFuture<Boolean> deferNextConnection(Player player) {
        var connection = new CompletableFuture<Boolean>();
        nextConnections.put(player.getUniqueId(), connection);
        return connection;
    }

    private ConnectionRequestBuilder request(UUID player, RegisteredServer target) {
        requested.computeIfAbsent(player, id -> new ArrayList<>()).add(target);
        return (ConnectionRequestBuilder) Proxy.newProxyInstance(ConnectionRequestBuilder.class.getClassLoader(),
                new Class<?>[]{ConnectionRequestBuilder.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "connectWithIndication" -> Optional.ofNullable(nextConnections.remove(player))
                            .orElseGet(() -> CompletableFuture.completedFuture(true));
                    default -> throw new AssertionError(method.getName());
                });
    }

    private Optional<Player> byName(String name) {
        return new ArrayList<>(online.values()).stream().filter(player -> player.getUsername().equalsIgnoreCase(name)).findFirst();
    }

    interface Sent {
        List<byte[]> sent();
    }
}
