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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetworkPackTest {

    private static final UUID PACK = UUID.nameUUIDFromBytes("blockcraft:pack".getBytes(StandardCharsets.UTF_8));

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final UUID id = UUID.randomUUID();
    private final Player player = proxy(Player.class, (self, method, args) -> switch (method) {
        case "getUniqueId" -> id;
        case "getUsername" -> "Steve";
        case "getAppliedResourcePacks" -> List.of();
        case "getCurrentServer" -> Optional.empty();
        case "sendResourcePackOffer" -> null;
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
        assertFalse("still loading", joined.get());
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.SUCCESSFUL, null));
        assertTrue("loaded", joined.get());
    }

    @Test
    public void onlyTheSessionsFirstOfferWaits() throws Exception {
        var pack = pack();
        resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby)));
        pack.onStatus(new PlayerResourcePackStatusEvent(player, PACK, Status.FAILED_DOWNLOAD, null));
        assertTrue("switching servers after a failed download",
                resumed(pack.onConfiguration(new PlayerConfigurationEvent(player, lobby))).get());
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
        Files.writeString(folder.getRoot().toPath().resolve("blockcraft.zip"), "pack");
        var info = proxy(ResourcePackInfo.class, (self, method, args) -> switch (method) {
            case "getUrl" -> "http://localhost/blockcraft.zip";
            default -> throw new AssertionError(method);
        });
        var builder = proxy(ResourcePackInfo.Builder.class, (self, method, args) -> method.equals("build") ? info : self);
        var server = proxy(ProxyServer.class, (self, method, args) -> switch (method) {
            case "createResourcePackBuilder" -> builder;
            default -> throw new AssertionError(method);
        });
        var config = new SystemConfig.ResourcePack("blockcraft.zip", "http://localhost/blockcraft.zip", 0, true, 60);
        return NetworkPack.start(server, config, folder.getRoot().toPath(), Component.empty(),
                LoggerFactory.getLogger(NetworkPackTest.class)).orElseThrow();
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
