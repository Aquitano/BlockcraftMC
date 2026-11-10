package me.blockcraft.system.pack;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent.Status;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import me.blockcraft.common.message.Messages;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NetworkPackTest {

    private static final UUID PACK = UUID.nameUUIDFromBytes("blockcraft:pack".getBytes(StandardCharsets.UTF_8));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final UUID id = UUID.randomUUID();
    private CompletableFuture<Component> disconnected = new CompletableFuture<>();
    private final AtomicInteger offers = new AtomicInteger();
    private final Player player = proxy(Player.class, (self, method, args) -> switch (method) {
        case "getUniqueId" -> id;
        case "getUsername" -> "Steve";
        case "getAppliedResourcePacks" -> List.of();
        case "getCurrentServer" -> Optional.empty();
        case "sendResourcePackOffer" -> { offers.incrementAndGet(); yield null; }
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

    private NetworkPack pack() throws Exception {
        return pack(true, 60);
    }

    private NetworkPack pack(boolean required, int waitSeconds) throws Exception {
        Files.writeString(folder.getRoot().toPath().resolve("blockcraft.zip"), "pack");
        return start(new SystemConfig.ResourcePack("blockcraft.zip", "http://localhost/blockcraft.zip", 0,
                required, waitSeconds)).orElseThrow();
    }

    private Optional<NetworkPack> start(SystemConfig.ResourcePack config) {
        var info = proxy(ResourcePackInfo.class, (self, method, args) -> switch (method) {
            case "getUrl" -> "http://localhost/blockcraft.zip";
            default -> throw new AssertionError(method);
        });
        var builder = proxy(ResourcePackInfo.Builder.class, (self, method, args) -> method.equals("build") ? info : self);
        var server = proxy(ProxyServer.class, (self, method, args) -> switch (method) {
            case "createResourcePackBuilder" -> builder;
            default -> throw new AssertionError(method);
        });
        return NetworkPack.start(server, config, folder.getRoot().toPath(),
                Messages.load(folder.getRoot().toPath(), () -> getClass().getResourceAsStream("/messages.yml")),
                LoggerFactory.getLogger(NetworkPackTest.class));
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
