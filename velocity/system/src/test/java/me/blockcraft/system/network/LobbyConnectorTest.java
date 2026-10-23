package me.blockcraft.system.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.command.player.PlayCommand;
import me.blockcraft.system.party.PartyManager;
import net.kyori.adventure.text.Component;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LobbyConnectorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final UUID playerId = UUID.randomUUID();
    private final Map<String, RegisteredServer> registered = new HashMap<>();
    private final List<Object> received = new ArrayList<>();
    private final List<RegisteredServer> requested = new ArrayList<>();
    private String currentServer;
    private int indicatedConnections;

    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (fake, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "getCurrentServer" -> connection();
                case "sendMessage" -> received.add(args[0]);
                case "createConnectionRequest" -> request((RegisteredServer) args[0]);
                case "hashCode" -> playerId.hashCode();
                case "equals" -> fake == args[0];
                default -> throw new AssertionError(method.getName());
            });

    @Test
    public void aPlayerAlreadyOnAFullLobbyHearsThatTheyAreAlreadyThere() throws Exception {
        currentServer = "lobby-1";
        var fixture = fixture(server("lobby-1", "lobby", 8, 8));

        fixture.connector().send(player);

        assertEquals(List.of(Component.text("Already on a lobby")), received);
        assertTrue(requested.isEmpty());
    }

    @Test
    public void anUnavailableLobbyDoesNotStartAConnection() throws Exception {
        var fixture = fixture(server("lobby-1", "lobby", 8, 8));

        fixture.connector().send(player);

        assertEquals(List.of(Component.text("No lobby available")), received);
        assertTrue(requested.isEmpty());
    }

    @Test
    public void aHubConnectionUsesVelocityFailureFeedback() throws Exception {
        var fixture = fixture(server("lobby-1", "lobby", 0, 8));

        fixture.connector().send(player);

        assertEquals(List.of(Component.text("Connecting to lobby")), received);
        assertEquals(List.of(registered.get("lobby-1")), requested);
        assertEquals(1, indicatedConnections);
    }

    @Test
    public void mixedCaseGamesCompleteAndConnectRegardlessOfTheHostLocale() throws Exception {
        var fixture = fixture(server("quicksg-1", "quicksg", 0, 8));
        var dispatcher = new CommandDispatcher<CommandSource>();
        dispatcher.getRoot().addChild(new PlayCommand(new CommandSupport(null, fixture.messages()),
                fixture.connector(), fixture.directory()).create().getNode());
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("play QUICK", player)).get();
            assertEquals(List.of("quicksg"), suggestions.getList().stream().map(Suggestion::getText).toList());

            dispatcher.execute("play QUICKSG", player);

            assertEquals(List.of(Component.text("Connecting to QuickSG")), received);
            assertEquals(List.of(registered.get("quicksg-1")), requested);
            assertEquals(1, indicatedConnections);
        } finally {
            Locale.setDefault(original);
        }
    }

    private Fixture fixture(ServerRecord... records) throws Exception {
        var proxy = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(), new Class<?>[]{ProxyServer.class},
                (fake, method, args) -> switch (method.getName()) {
                    case "getServer" -> Optional.ofNullable(registered.get(args[0]));
                    default -> throw new AssertionError(method.getName());
                });
        for (var record : records) {
            var info = new ServerInfo(record.name(), InetSocketAddress.createUnresolved("127.0.0.1", 25566));
            var server = (RegisteredServer) Proxy.newProxyInstance(RegisteredServer.class.getClassLoader(),
                    new Class<?>[]{RegisteredServer.class}, (fake, method, args) -> switch (method.getName()) {
                        case "getServerInfo" -> info;
                        case "getPlayersConnected" -> record.name().equals(currentServer) ? List.of(player) : List.of();
                        case "hashCode" -> info.hashCode();
                        case "equals" -> fake == args[0];
                        default -> throw new AssertionError(method.getName());
                    });
            registered.put(record.name(), server);
        }
        var directory = new ServerDirectory(proxy, "lobby", Duration.ofSeconds(15), counts -> { });
        directory.update(List.of(records));
        var data = folder.newFolder().toPath();
        var config = SystemConfig.load(data.resolve("config.yml"), () -> bytes("lobby-group: lobby\n"));
        var messages = Messages.load(data, () -> bytes("""
                hub:
                  already: 'Already on a lobby'
                  unavailable: 'No lobby available'
                  connecting: 'Connecting to lobby'
                play:
                  connecting: 'Connecting to <game>'
                  games:
                    quicksg: 'QuickSG'
                """));
        var parties = new PartyManager(proxy, null, null, null, directory, messages);
        return new Fixture(new LobbyConnector(directory, parties, config, messages), directory, messages);
    }

    private Optional<ServerConnection> connection() {
        return Optional.ofNullable(currentServer).map(name ->
                (ServerConnection) Proxy.newProxyInstance(ServerConnection.class.getClassLoader(), new Class<?>[]{ServerConnection.class},
                        (fake, method, args) -> switch (method.getName()) {
                            case "getServer" -> registered.get(name);
                            default -> throw new AssertionError(method.getName());
                        }));
    }

    private ConnectionRequestBuilder request(RegisteredServer target) {
        requested.add(target);
        return (ConnectionRequestBuilder) Proxy.newProxyInstance(ConnectionRequestBuilder.class.getClassLoader(),
                new Class<?>[]{ConnectionRequestBuilder.class}, (fake, method, args) -> switch (method.getName()) {
                    case "connectWithIndication" -> {
                        indicatedConnections++;
                        yield CompletableFuture.completedFuture(true);
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static ServerRecord server(String name, String group, int online, int maxPlayers) {
        return new ServerRecord(new ServerIdentity(name, group, "127.0.0.1", 25566),
                new ServerStatus(ServerState.LOBBY, online, maxPlayers, 20, 5), 0);
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(LobbyConnector connector, ServerDirectory directory, Messages messages) {
    }
}
