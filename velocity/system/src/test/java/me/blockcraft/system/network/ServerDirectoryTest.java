package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ServerDirectoryTest {

    private static final Duration SEAT_TIMEOUT = Duration.ofSeconds(15);

    private final Map<String, RegisteredServer> registered = new HashMap<>();
    private final ProxyServer proxy = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
            new Class<?>[]{ProxyServer.class}, (fake, method, args) -> switch (method.getName()) {
                case "getServer" -> Optional.of(registered.computeIfAbsent((String) args[0], ServerDirectoryTest::nobodyOn));
                default -> throw new AssertionError(method.getName());
            });
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    public void onlyAGameServerWithARunningRoundIsWatchable() {
        var directory = directoryOf(
                server("quicksg-1", "quicksg", ServerState.INGAME),
                server("quicksg-2", "quicksg", ServerState.LOBBY),
                server("quicksg-3", "quicksg", ServerState.RESTART),
                server("lobby-1", "lobby", ServerState.INGAME));

        assertTrue(directory.isWatchable("quicksg-1"));
        assertFalse(directory.isWatchable("quicksg-2"));
        assertFalse(directory.isWatchable("quicksg-3"));
        assertFalse(directory.isWatchable("lobby-1"));
        assertFalse(directory.isWatchable("unknown"));
    }

    @Test
    public void aRunningRoundStaysClosedToJoins() {
        var directory = directoryOf(server("quicksg-1", "quicksg", ServerState.INGAME));

        assertFalse(directory.isJoinable("quicksg-1"));
    }

    @Test
    public void aBurstOfJoinsMovesOnOnceAnInstanceIsFull() {
        var directory = directoryOf(game("quicksg-1", 0, 2), game("quicksg-2", 0, 2));

        String first = fill(directory, UUID.randomUUID()).orElseThrow();
        assertEquals(Optional.of(first), fill(directory, UUID.randomUUID()));
        String third = fill(directory, UUID.randomUUID()).orElseThrow();
        assertNotEquals(first, third);
    }

    @Test
    public void aPartyGoesWhereAllOfItFits() {
        var directory = directoryOf(game("quicksg-1", 6, 8), game("quicksg-2", 2, 8));

        assertEquals(Optional.of("quicksg-2"), fill(directory, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    public void aReleasedSeatIsFreeAgain() {
        var directory = directoryOf(game("quicksg-1", 1, 2));
        var player = UUID.randomUUID();

        fill(directory, player);
        assertFalse(directory.isJoinable("quicksg-1"));
        directory.release(player, "quicksg-1");
        assertTrue(directory.isJoinable("quicksg-1"));
    }

    @Test
    public void anExpiredSeatIsFreeAgain() {
        var directory = directoryOf(game("quicksg-1", 1, 2));

        fill(directory, UUID.randomUUID());
        assertFalse(directory.isJoinable("quicksg-1"));
        now = now.plus(SEAT_TIMEOUT);
        assertTrue(directory.isJoinable("quicksg-1"));
    }

    /** Nobody is connected through the fake proxy, so the mirrored rows and the seats are all that count. */
    private ServerDirectory directoryOf(ServerRecord... servers) {
        var directory = new ServerDirectory(proxy, "lobby", SEAT_TIMEOUT, () -> now);
        directory.update(List.of(servers));
        return directory;
    }

    private static Optional<String> fill(ServerDirectory directory, UUID... players) {
        return directory.fill("quicksg", List.of(players)).map(server -> server.getServerInfo().getName());
    }

    private static ServerRecord server(String name, String group, ServerState state) {
        return new ServerRecord(new ServerIdentity(name, group, "127.0.0.1", 25566), new ServerStatus(state, 4, 8, 20, 5));
    }

    private static ServerRecord game(String name, int online, int maxPlayers) {
        return new ServerRecord(new ServerIdentity(name, "quicksg", "127.0.0.1", 25566),
                new ServerStatus(ServerState.LOBBY, online, maxPlayers, 20, 5));
    }

    private static RegisteredServer nobodyOn(String name) {
        var info = new ServerInfo(name, InetSocketAddress.createUnresolved("127.0.0.1", 25566));
        return (RegisteredServer) Proxy.newProxyInstance(RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class}, (fake, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "getPlayersConnected" -> List.of();
                    case "hashCode" -> info.hashCode();
                    case "equals" -> fake == args[0];
                    default -> throw new AssertionError(method.getName());
                });
    }
}
