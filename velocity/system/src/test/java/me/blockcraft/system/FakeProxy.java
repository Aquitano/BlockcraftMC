package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Just enough of the Velocity API for the unit tests: players with a name, id and permissions on a proxy that lists
 * them, backends a player can be connected to, and the messages each side received.
 */
final class FakeProxy {

    private final Map<UUID, Player> online = new LinkedHashMap<>();
    private final Map<UUID, ServerConnection> connections = new LinkedHashMap<>();
    private final Map<UUID, List<Object>> received = new LinkedHashMap<>();

    final ChannelRegistrar registrar = (ChannelRegistrar) Proxy.newProxyInstance(ChannelRegistrar.class.getClassLoader(),
            new Class<?>[]{ChannelRegistrar.class}, (proxy, method, args) -> switch (method.getName()) {
                case "register", "unregister" -> null;
                default -> throw new AssertionError(method.getName());
            });

    final ProxyServer server = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
            new Class<?>[]{ProxyServer.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getAllPlayers" -> List.copyOf(online.values());
                case "getPlayer" -> args[0] instanceof UUID id ? Optional.ofNullable(online.get(id)) : byName(String.valueOf(args[0]));
                case "getChannelRegistrar" -> registrar;
                default -> throw new AssertionError(method.getName());
            });

    Player join(String name, String... permissions) {
        var id = UUID.randomUUID();
        var granted = Set.of(permissions);
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getUsername", "toString" -> name;
                    case "hasPermission" -> granted.contains(args[0]);
                    case "isActive" -> online.containsKey(id);
                    case "getCurrentServer" -> Optional.ofNullable(connections.get(id));
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
    ServerConnection connect(Player player, String serverName) {
        var info = new ServerInfo(serverName, new InetSocketAddress("127.0.0.1", 25566));
        var sent = new ArrayList<byte[]>();
        var connection = (ServerConnection) Proxy.newProxyInstance(ServerConnection.class.getClassLoader(),
                new Class<?>[]{ServerConnection.class, Sent.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> player;
                    case "getServerInfo" -> info;
                    case "sendPluginMessage" -> sent.add((byte[]) args[1]);
                    case "sent" -> List.copyOf(sent);
                    case "hashCode" -> info.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method.getName());
                });
        connections.put(player.getUniqueId(), connection);
        return connection;
    }

    List<byte[]> sentTo(ServerConnection connection) {
        return ((Sent) connection).sent();
    }

    List<Object> received(Player player) {
        return received.getOrDefault(player.getUniqueId(), List.of());
    }

    private Optional<Player> byName(String name) {
        return new ArrayList<>(online.values()).stream().filter(player -> player.getUsername().equalsIgnoreCase(name)).findFirst();
    }

    interface Sent {
        List<byte[]> sent();
    }
}
