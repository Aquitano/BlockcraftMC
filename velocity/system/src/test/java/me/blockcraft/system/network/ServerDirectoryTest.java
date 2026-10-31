package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import me.blockcraft.system.FakeProxy;
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

    @Test
    public void privateDuelsKeepStrangersOutUntilTheirOwnerReleasesTheArena() {
        var directory = directoryOf(game("quicksg-1", 0, 2));
        var players = java.util.Set.of(UUID.randomUUID(), UUID.randomUUID());
        assertTrue(directory.reserveDuel("quicksg", players).isPresent());
        assertTrue(fill(directory, UUID.randomUUID()).isEmpty());
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(UUID.randomUUID(), "quicksg-1"));
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(players.iterator().next(), "quicksg-1"));
        directory.releaseDuel("quicksg-1", java.util.Set.of(UUID.randomUUID(), UUID.randomUUID()));
        assertTrue(fill(directory, UUID.randomUUID()).isEmpty());
        directory.releaseDuel("quicksg-1", players);
        assertTrue(fill(directory, UUID.randomUUID()).isPresent());
    }

    private static final Duration SEAT_TIMEOUT = Duration.ofSeconds(15);

    private final Map<String, RegisteredServer> registered = new HashMap<>();
    private final ProxyServer proxy = (ProxyServer) Proxy.newProxyInstance(ProxyServer.class.getClassLoader(),
            new Class<?>[]{ProxyServer.class}, (fake, method, args) -> switch (method.getName()) {
                case "getServer" -> Optional.of(registered.computeIfAbsent((String) args[0], ServerDirectoryTest::nobodyOn));
                default -> throw new AssertionError(method.getName());
            });
    private final Map<String, Integer> published = new HashMap<>();
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    public void staleDiscoveryClosesNewAdmissionsUntilAFreshRead() {
        var directory = directoryOf(game("quicksg-1", 0, 8),
                server("playing", "quicksg", ServerState.INGAME));
        var reserved = UUID.randomUUID();
        assertTrue(directory.getForRouting("playing").isPresent());
        assertEquals(Optional.of("quicksg-1"), fill(directory, reserved));
        now = now.plusSeconds(29);
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(reserved, "quicksg-1"));

        now = now.plusSeconds(1);
        assertEquals(Optional.empty(), fill(directory, UUID.randomUUID()));
        assertEquals(Optional.empty(), directory.balance("quicksg"));
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(reserved, "quicksg-1"));
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(UUID.randomUUID(), "playing"));
        assertFalse(directory.admit(UUID.randomUUID(), "quicksg-1"));
        assertFalse(directory.idle("quicksg"));
        assertFalse(directory.drainIfIdle("quicksg"));
        assertEquals(Optional.of("quicksg"), directory.groupOf("playing"));
        assertTrue(directory.get("playing").isPresent());
        assertTrue(directory.getForRouting("playing").isEmpty());

        directory.update(List.of(game("quicksg-1", 0, 8)));
        assertEquals(Optional.of("quicksg-1"), fill(directory, UUID.randomUUID()));
    }

    @Test
    public void staleEmptyGroupsDoNotDrainAndLobbyIdentityRemainsReadable() {
        var directory = new ServerDirectory(proxy, "lobby", SEAT_TIMEOUT, Duration.ofSeconds(7),
                () -> now, published::putAll);
        directory.update(List.of(game("quicksg-1", 0, 8), server("lobby-1", "lobby", ServerState.LOBBY)));
        assertTrue(directory.idle("quicksg"));
        assertTrue(directory.admit(UUID.randomUUID(), "lobby-1"));

        now = now.plusSeconds(7);

        assertFalse(directory.idle("quicksg"));
        assertFalse(directory.drainIfIdle("quicksg"));
        assertFalse(directory.admit(UUID.randomUUID(), "lobby-1"));
        assertEquals(Optional.empty(), directory.balance("lobby"));
        assertTrue(directory.isLobby("lobby-1"));
        assertTrue(directory.running("quicksg"));
    }

    @Test
    public void anExpiredSelectionCannotStartAConnectionUntilDiscoveryRecovers() {
        var fake = new FakeProxy();
        var player = fake.join("Player");
        var target = fake.connect(fake.join("Target"), "quicksg-1").getServer();
        var directory = new ServerDirectory(fake.server, "lobby", SEAT_TIMEOUT, () -> now, counts -> { });
        directory.update(List.of(game("quicksg-1", 0, 8)));
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(player.getUniqueId(), "quicksg-1"));
        now = now.plusSeconds(30);

        assertFalse(directory.send(player, target).join());
        assertTrue(fake.requested(player).isEmpty());

        directory.update(List.of(game("quicksg-1", 0, 8)));
        assertTrue(directory.send(player, target).join());
        assertEquals(List.of(target), fake.requested(player));
    }

    @Test
    public void continuousGamesSeatPlayersAndOnlyDrainWhenEmpty() {
        var directory = directoryOf(new ServerRecord(new ServerIdentity("ffa", "knockbackffa", "127.0.0.1", 25566),
                new ServerStatus(ServerState.OPEN, 0, 8, 20, 5), 0));

        assertTrue(directory.idle("knockbackffa"));
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(UUID.randomUUID(), "ffa"));
        assertFalse(directory.isWatchable("ffa"));
        assertFalse(directory.drainIfIdle("knockbackffa"));
    }

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
    public void aRunningRoundIsWatchedRatherThanJoined() {
        var directory = directoryOf(server("quicksg-1", "quicksg", ServerState.INGAME));

        assertEquals(ServerDirectory.Entry.WATCHING, directory.enter(UUID.randomUUID(), "quicksg-1"));
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
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(UUID.randomUUID(), "quicksg-1"));
        directory.release(player, "quicksg-1");
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(UUID.randomUUID(), "quicksg-1"));
    }

    @Test
    public void anExpiredSeatIsFreeAgain() {
        var directory = directoryOf(game("quicksg-1", 1, 2));

        fill(directory, UUID.randomUUID());
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(UUID.randomUUID(), "quicksg-1"));
        now = now.plus(SEAT_TIMEOUT);
        assertEquals(ServerDirectory.Entry.SEATED, directory.enter(UUID.randomUUID(), "quicksg-1"));
    }

    @Test
    public void signsHearOfASeatBeforeTheHeartbeatDoes() {
        var directory = directoryOf(game("quicksg-1", 1, 8));
        var player = UUID.randomUUID();

        fill(directory, player);
        assertEquals(Integer.valueOf(2), published.get("quicksg-1"));
        directory.release(player, "quicksg-1");
        assertEquals(Integer.valueOf(1), published.get("quicksg-1"));
    }

    @Test
    public void aDrainingGroupSeatsNobodyAndOnlyAnIdleOneDrains() {
        var directory = directoryOf(game("quicksg-1", 0, 8));
        var player = UUID.randomUUID();

        fill(directory, player);
        assertFalse(directory.idle("quicksg"));
        assertFalse(directory.drainIfIdle("quicksg"));
        directory.release(player, "quicksg-1");
        assertTrue(directory.drainIfIdle("quicksg"));
        assertEquals(Optional.empty(), fill(directory, UUID.randomUUID()));
        assertEquals(ServerDirectory.Entry.CLOSED, directory.enter(UUID.randomUUID(), "quicksg-1"));
        directory.undrain("quicksg");
        assertEquals(Optional.of("quicksg-1"), fill(directory, UUID.randomUUID()));
    }

    /** Nobody is connected through the fake proxy, so the mirrored rows and the seats are all that count. */
    private ServerDirectory directoryOf(ServerRecord... servers) {
        var directory = new ServerDirectory(proxy, "lobby", SEAT_TIMEOUT, () -> now, published::putAll);
        directory.update(List.of(servers));
        return directory;
    }

    private static Optional<String> fill(ServerDirectory directory, UUID... players) {
        return directory.fill("quicksg", List.of(players)).map(server -> server.getServerInfo().getName());
    }

    private static ServerRecord server(String name, String group, ServerState state) {
        return new ServerRecord(new ServerIdentity(name, group, "127.0.0.1", 25566), new ServerStatus(state, 4, 8, 20, 5), 0);
    }

    private static ServerRecord game(String name, int online, int maxPlayers) {
        return new ServerRecord(new ServerIdentity(name, "quicksg", "127.0.0.1", 25566),
                new ServerStatus(ServerState.LOBBY, online, maxPlayers, 20, 5), 0);
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
