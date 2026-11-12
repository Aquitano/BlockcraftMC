package me.blockcraft.system.pack;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent.Status;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.PackStatus;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.Component;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NetworkPackTest {

    private static final UUID PACK = UUID.nameUUIDFromBytes("blockcraft:pack".getBytes(StandardCharsets.UTF_8));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final UUID id = UUID.randomUUID();
    private CompletableFuture<Component> disconnected = new CompletableFuture<>();
    private final Map<UUID, ResourcePackInfo> offeredPacks = new HashMap<>();
    private final List<UUID> offered = new ArrayList<>();
    private final List<ProxyMessage> backend = new ArrayList<>();
    private final List<UUID> removed = new ArrayList<>();
    private Optional<ServerConnection> current = Optional.empty();
    private final AtomicInteger offers = new AtomicInteger();
    private final Player player = proxy(Player.class, (self, method, args) -> switch (method) {
        case "getUniqueId" -> id;
        case "getUsername" -> "Steve";
        case "getAppliedResourcePacks" -> List.of();
        case "getCurrentServer" -> current;
        case "sendResourcePackOffer" -> {
            offers.incrementAndGet();
            var info = (ResourcePackInfo) args[0];
            offered.add(info.getId());
            offeredPacks.put(info.getId(), info);
            yield null;
        }
        case "sendMessage" -> null;
        case "removeResourcePacks" -> { removed.add((UUID) args[0]); yield null; }
        case "disconnect" -> { disconnected.complete((Component) args[0]); yield null; }
        default -> throw new AssertionError(method);
    });
    private final ServerConnection lobby = proxy(ServerConnection.class, (self, method, args) -> {
        throw new AssertionError(method);
    });

    @Test
    public void aJoiningPlayerWaitsUntilTheirClientHasLoadedThePack() throws Exception {
        var pack = pack();
        var joined = resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.ACCEPTED, null));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.DOWNLOADED, null));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, UUID.randomUUID(), Status.SUCCESSFUL, null));
        assertFalse("still loading", joined.get());
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.SUCCESSFUL, null));
        assertTrue("loaded", joined.get());
    }

    @Test
    public void onlyTheSessionsFirstOptionalOfferWaits() throws Exception {
        var pack = pack(false, 60);
        resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.FAILED_DOWNLOAD, null));
        assertTrue("switching servers after a failed download",
                resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby))).get());
    }

    @Test
    public void requiredPackFailuresDisconnectBeforeConfigurationCanResume() throws Exception {
        for (var status : List.of(Status.DECLINED, Status.FAILED_DOWNLOAD, Status.FAILED_RELOAD,
                Status.INVALID_URL, Status.DISCARDED)) {
            var pack = pack();
            disconnected = new CompletableFuture<>();
            var resumedWithoutDisconnect = new AtomicBoolean();
            var resumed = new AtomicBoolean();
            pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)).execute(new Continuation() {
                @Override
                public void resume() {
                    resumedWithoutDisconnect.set(disconnected.getNow(null) == null);
                    resumed.set(true);
                }

                @Override
                public void resumeWithException(Throwable exception) {
                    throw new AssertionError(exception);
                }
            });
            pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, status, null));
            assertTrue(status.name(), resumed.get());
            assertFalse(status.name(), resumedWithoutDisconnect.get());
            var offered = offers.get();
            pack.retry(player);
            assertEquals("failed required pack cannot be offered again in a closing session", offered, offers.get());
        }
    }

    @Test
    public void requiredPackTimeoutDisconnectsBeforeConfigurationResumes() throws Exception {
        var pack = pack(true, 0);
        var continued = new CompletableFuture<Boolean>();
        pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)).execute(new Continuation() {
            @Override
            public void resume() {
                continued.complete(disconnected.isDone());
            }

            @Override
            public void resumeWithException(Throwable exception) {
                continued.completeExceptionally(exception);
            }
        });
        assertTrue(continued.get(5, TimeUnit.SECONDS));
    }

    @Test
    public void aCompletedLoadOrDisconnectedSessionCannotBeDisconnectedByItsOldTimeout() throws Exception {
        var pack = pack(true, 1);
        var joined = resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.SUCCESSFUL, null));
        assertTrue(joined.get());
        var closed = pack(true, 1);
        var released = resumed(closed.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        closed.onDisconnect(new DisconnectEvent(player, DisconnectEvent.LoginStatus.PRE_SERVER_JOIN));
        assertTrue(released.get());
        assertThrows(TimeoutException.class, () -> disconnected.get(1500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void aMissingRequiredPackBlocksEntry() throws Exception {
        var pack = start(new SystemConfig.ResourcePack("missing.zip", "http://localhost/missing.zip", 0, true, 60))
                .orElseThrow();
        resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        assertTrue(disconnected.isDone());
        assertEquals(0, offers.get());
        assertTrue(start(new SystemConfig.ResourcePack("missing.zip", "http://localhost/missing.zip", 0, false, 60))
                .isEmpty());
    }

    @Test
    public void invalidRequiredPackConfigurationStillBlocksEntry() throws Exception {
        Files.writeString(folder.getRoot().toPath().resolve("blockcraft.zip"), "pack");
        var pack = start(new SystemConfig.ResourcePack("blockcraft.zip", "http://localhost/blockcraft.zip", 70000, true, 60))
                .orElseThrow();
        resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        assertTrue(disconnected.isDone());
        assertEquals(0, offers.get());
        assertTrue(start(new SystemConfig.ResourcePack("blockcraft.zip", "http://localhost/blockcraft.zip", 70000, false, 60))
                .isEmpty());
    }

    @Test
    public void aRejectedDuplicateLoginDoesNotReleaseTheActiveSessionsPackWait() throws Exception {
        var pack = pack();
        var joined = resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));

        pack.onDisconnect(new DisconnectEvent(player, DisconnectEvent.LoginStatus.CONFLICTING_LOGIN));
        assertFalse("the active session is still loading its pack", joined.get());

        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.SUCCESSFUL, null));
        assertTrue("the active session loaded its pack", joined.get());
    }

    @Test
    public void lobbyAndModesRemoveOnlyUnneededAddonsAfterArrival() throws Exception {
        var pack = profiles(60);
        var joined = configure(pack, "lobby");
        assertEquals(List.of(PACK, addon("lobby")), offered);
        for (var name : List.of("base", "lobby")) {
            var info = offeredPacks.get(name.equals("base") ? PACK : addon(name));
            assertEquals("http://localhost/blockcraft-" + name + ".zip", info.getUrl());
            assertArrayEquals(java.security.MessageDigest.getInstance("SHA-1").digest(name.getBytes(StandardCharsets.UTF_8)),
                    info.getHash());
        }
        success(pack, PACK);
        success(pack, addon("lobby"));
        assertTrue(joined.get());
        arrive(pack, "lobby");
        var modes = configure(pack, "bedwars");
        assertTrue(removed.isEmpty());
        success(pack, addon("modes"));
        assertTrue(modes.get());
        arrive(pack, "bedwars");
        assertEquals(java.util.Set.of(addon("lobby")), java.util.Set.copyOf(removed));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, addon("starwars"), Status.DISCARDED, null));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, addon("starwars"), Status.SUCCESSFUL, null));
        assertFalse(disconnected.isDone());
        var returning = configure(pack, "lobby");
        pack.onStatus(new PlayerResourcePackStatusEvent(player, addon("lobby"), Status.DISCARDED, null));
        assertFalse(disconnected.isDone());
        assertFalse(returning.get());
        success(pack, addon("lobby"));
        assertTrue(returning.get());
    }

    @Test
    public void starWarsEntryWaitsForItsAddonAndSwitchingInstancesDoesNotOfferItAgain() throws Exception {
        var pack = profiles(60);
        var joined = configure(pack, "sternenfront");
        current = Optional.of(connection("sternenfront"));
        success(pack, PACK);
        assertEquals(new ProxyMessage.Pack(PackStatus.APPLIED), backend.getLast());
        assertFalse("base alone cannot release Star Wars entry", joined.get());
        success(pack, addon("starwars"));
        assertTrue(joined.get());
        arrive(pack, "sternenfront");
        assertTrue(configure(pack, "sternenfront-2").get());
        assertEquals(List.of(PACK, addon("starwars")), offered);
        arrive(pack, "sternenfront-2");
        var lobby = configure(pack, "lobby");
        success(pack, addon("lobby"));
        assertTrue(lobby.get());
        arrive(pack, "lobby");
        assertEquals(List.of(addon("starwars")), removed);
        assertEquals(List.of(PACK, addon("starwars"), addon("lobby")), offered);
        var backToStarWars = configure(pack, "sternenfront");
        assertFalse(backToStarWars.get());
        success(pack, addon("starwars"));
        assertTrue(backToStarWars.get());
        arrive(pack, "sternenfront");
        assertEquals(List.of(addon("starwars"), addon("lobby")), removed);
        var returning = configure(pack, "lobby");
        success(pack, addon("lobby"));
        assertTrue(returning.get());
        arrive(pack, "lobby");
        assertEquals(List.of(PACK, addon("starwars"), addon("lobby"), addon("starwars"), addon("lobby")), offered);
    }

    @Test
    public void lobbyRemovesStarWarsOnlyAfterArrival() throws Exception {
        var pack = profiles(60, 0, List.of("lobby"));
        configure(pack, "sternenfront");
        success(pack, PACK);
        success(pack, addon("starwars"));
        arrive(pack, "sternenfront");
        var lobby = configure(pack, "lobby");
        success(pack, addon("lobby"));
        assertTrue(lobby.get());
        assertTrue(removed.isEmpty());
        arrive(pack, "lobby");
        assertEquals(List.of(addon("starwars")), removed);
    }

    @Test
    public void supersededWaitCannotTimeoutOrRemoveTheNewDestinationsPack() throws Exception {
        var pack = profiles(1);
        var old = configure(pack, "sternenfront");
        var latest = configure(pack, "bedwars");
        assertTrue(old.get());
        success(pack, PACK);
        success(pack, addon("modes"));
        assertTrue(latest.get());
        arrive(pack, "sternenfront");
        assertTrue(removed.isEmpty());
        arrive(pack, "bedwars");
        pack.onStatus(new PlayerResourcePackStatusEvent(player, addon("starwars"), Status.FAILED_DOWNLOAD, null));
        assertThrows(TimeoutException.class, () -> disconnected.get(1500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void targetAddonFailureDisconnectsButBaseSuccessDoesNotMaskIt() throws Exception {
        var pack = profiles(60);
        var joined = configure(pack, "sternenfront");
        success(pack, PACK);
        pack.onStatus(new PlayerResourcePackStatusEvent(player, addon("starwars"), Status.FAILED_RELOAD, null));
        assertTrue(disconnected.isDone());
        assertTrue(joined.get());
    }

    @Test
    public void targetAddonTimeoutDisconnectsAndKickCancelsAnAbortedWait() throws Exception {
        var timeout = profiles(1);
        var continued = new CompletableFuture<Boolean>();
        var configuration = timeout.onConfiguration(new PlayerConfigurationEvent(player, connection("sternenfront")));
        success(timeout, PACK);
        configuration.execute(new Continuation() {
                    @Override
                    public void resume() { continued.complete(disconnected.isDone()); }
                    @Override
                    public void resumeWithException(Throwable failure) { continued.completeExceptionally(failure); }
                });
        assertTrue(continued.get(5, TimeUnit.SECONDS));
        disconnected = new CompletableFuture<>();
        var aborted = profiles(1);
        var waiting = configure(aborted, "sternenfront");
        aborted.onKick(new KickedFromServerEvent(player, registered("sternenfront"), Component.empty(), true,
                KickedFromServerEvent.Notify.create(Component.empty())));
        assertTrue(waiting.get());
        assertThrows(TimeoutException.class, () -> disconnected.get(1500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void splitReplayEntryIsExplicitlyBlockedUntilItsOriginIsKnown() throws Exception {
        var pack = profiles(60);
        assertTrue(configure(pack, "replay").get());
        assertTrue(disconnected.isDone());
        assertTrue(offered.isEmpty());
    }

    @Test
    public void replayLoadsItsRecordedGameAndCanSwitchProfilesBeforePlayback() throws Exception {
        var pack = profiles(60);
        pack.replay(player, "replay", "sternenfront");
        var joined = configure(pack, "replay");
        assertEquals(List.of(PACK, addon("starwars")), offered);
        success(pack, PACK);
        success(pack, addon("starwars"));
        assertTrue(joined.get());
        arrive(pack, "replay");
        var next = pack.prepareReplay(player, "bedwars");
        assertFalse(next.isDone());
        assertTrue(removed.isEmpty());
        success(pack, addon("modes"));
        assertTrue(next.get());
        assertEquals(List.of(addon("starwars")), removed);
        assertEquals(List.of(PACK, addon("starwars"), addon("modes")), offered);
    }

    @Test
    public void transientDownloadFailureRetriesWithoutReleasingEntry() throws Exception {
        var pack = profiles(60, 0, List.of("lobby"), 1);
        var joined = configure(pack, "sternenfront");
        success(pack, PACK);
        pack.onStatus(new PlayerResourcePackStatusEvent(player, addon("starwars"), Status.FAILED_DOWNLOAD, null));
        assertEquals(List.of(PACK, addon("starwars"), addon("starwars")), offered);
        assertFalse(joined.get());
        assertFalse(disconnected.isDone());
        success(pack, addon("starwars"));
        assertTrue(joined.get());
    }

    @Test
    public void repeatedDownloadFailureStopsAtTheConfiguredRetryLimit() throws Exception {
        var pack = profiles(60, 0, List.of("lobby"), 1);
        configure(pack, "sternenfront");
        var failed = new PlayerResourcePackStatusEvent(player, addon("starwars"), Status.FAILED_DOWNLOAD, null);
        pack.onStatus(failed);
        pack.onStatus(failed);
        assertTrue(disconnected.isDone());
        assertEquals(3, offered.size());
    }

    @Test
    public void httpServiceHostsEachConfiguredArchiveIndependently() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var pack = profiles(60, port);
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            for (var name : List.of("base", "lobby", "modes", "starwars")) {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://localhost:" + port + "/blockcraft-" + name + ".zip")).build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals(name, response.body());
            }
        } finally {
            pack.stop();
        }
    }

    private NetworkPack profiles(int waitSeconds) throws Exception {
        return profiles(waitSeconds, 0);
    }

    private NetworkPack profiles(int waitSeconds, int port) throws Exception {
        return profiles(waitSeconds, port, List.of("lobby"));
    }

    private NetworkPack profiles(int waitSeconds, int port, List<String> lobbyPacks) throws Exception {
        return profiles(waitSeconds, port, lobbyPacks, 0);
    }

    private NetworkPack profiles(int waitSeconds, int port, List<String> lobbyPacks, int retries) throws Exception {
        var archives = new HashMap<String, SystemConfig.PackArchive>();
        for (var name : List.of("base", "lobby", "modes", "starwars")) {
            var file = "blockcraft-" + name + ".zip";
            Files.writeString(folder.getRoot().toPath().resolve(file), name);
            archives.put(name, new SystemConfig.PackArchive(file, "http://localhost/" + file));
        }
        var profiles = new SystemConfig.PackProfiles(true, archives,
                Map.of("lobby", lobbyPacks, "sternenfront", List.of("starwars"), "replay", List.of()),
                List.of("modes"));
        return start(new SystemConfig.ResourcePack("blockcraft.zip", "http://localhost/blockcraft.zip", port, true,
                waitSeconds, profiles, retries)).orElseThrow();
    }

    private static UUID addon(String name) {
        return UUID.nameUUIDFromBytes(("blockcraft:pack:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private AtomicBoolean configure(NetworkPack pack, String group) {
        return resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, connection(group))));
    }

    private void success(NetworkPack pack, UUID id) {
        pack.onStatus(new PlayerResourcePackStatusEvent(player, id, Status.SUCCESSFUL, null));
    }

    private void arrive(NetworkPack pack, String group) {
        current = Optional.of(connection(group));
        pack.onServerPostConnect(new ServerPostConnectEvent(player, null));
    }

    private static RegisteredServer registered(String name) {
        return proxy(RegisteredServer.class, (self, method, args) -> switch (method) {
            case "getServerInfo" -> new ServerInfo(name, new InetSocketAddress("localhost", 25565));
            default -> throw new AssertionError(method);
        });
    }

    private ServerConnection connection(String name) {
        return proxy(ServerConnection.class, (self, method, args) -> switch (method) {
            case "getServerInfo" -> registered(name).getServerInfo();
            case "sendPluginMessage" -> {
                backend.add(ProxyMessageCodec.decode((byte[]) args[1], ProxyMessage.ToBackend.class).orElseThrow());
                yield true;
            }
            default -> throw new AssertionError(method);
        });
    }

    private NetworkPack pack() throws Exception {
        return pack(true, 60);
    }

    private NetworkPack pack(boolean required, int waitSeconds) throws Exception {
        Files.writeString(folder.getRoot().toPath().resolve("blockcraft.zip"), "pack");
        return start(new SystemConfig.ResourcePack("blockcraft.zip", "http://localhost/blockcraft.zip", 0,
                required, waitSeconds)).orElseThrow();
    }

    private Optional<NetworkPack> start(SystemConfig.ResourcePack config) {
        var server = proxy(ProxyServer.class, (self, method, args) -> {
            if (!method.equals("createResourcePackBuilder")) throw new AssertionError(method);
            var url = (String) args[0];
            var values = new HashMap<String, Object>();
            return proxy(ResourcePackInfo.Builder.class, (builder, operation, arguments) -> {
                if (operation.equals("build")) {
                    return proxy(ResourcePackInfo.class, (info, getter, ignored) -> switch (getter) {
                        case "getUrl" -> url;
                        case "getId" -> values.get("setId");
                        case "getHash" -> values.get("setHash");
                        default -> throw new AssertionError(getter);
                    });
                }
                values.put(operation, arguments[0]);
                return builder;
            });
        });
        return NetworkPack.start(server, config, folder.getRoot().toPath(),
                Messages.load(folder.getRoot().toPath(), () -> getClass().getResourceAsStream("/messages.yml")),
                LoggerFactory.getLogger(NetworkPackTest.class), name -> Optional.of(name.equals("sternenfront-2") ? "sternenfront" : name));
    }

    private static AtomicBoolean resumed(EventTask task) {
        var resumed = new AtomicBoolean();
        task.execute(new Continuation() {
            @Override
            public void resume() {
                resumed.set(true);
            }

            @Override
            public void resumeWithException(Throwable exception) {
                throw new AssertionError(exception);
            }
        });
        return resumed;
    }

    private interface Answer {
        Object apply(Object self, String method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> answer.apply(self, method.getName(), args)));
    }
}
