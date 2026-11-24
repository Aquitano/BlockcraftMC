package me.blockcraft.system.network;

import com.velocitypowered.api.event.connection.PostLoginEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import me.blockcraft.system.FakeProxy;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.listener.LoginListener;
import me.blockcraft.system.welcome.Anniversaries;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoginListenerTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void delayedWelcomeLeavesRejoinsAndImmediateGameSelectionsAlone() {
        var proxy = new FakeProxy();
        var player = proxy.join("Player");
        proxy.connect(proxy.join("LobbyPlayer"), "lobby");
        var directory = new ServerDirectory(proxy.server, "lobby", Duration.ofSeconds(15), counts -> { });
        directory.update(List.of(new ServerRecord(new ServerIdentity("lobby", "lobby", "localhost", 25566),
                new ServerStatus(ServerState.LOBBY, 1, 100, 20, 5), 0)));
        var config = SystemConfig.load(folder.getRoot().toPath().resolve("config.yml"),
                () -> getClass().getResourceAsStream("/config.yml"));
        var messages = Messages.load(folder.getRoot().toPath(), () -> getClass().getResourceAsStream("/messages.yml"));
        var lobby = new LobbyConnector(directory, null, config, messages, Optional.empty());
        var listener = new LoginListener(this, proxy.server, config, null, null, null, null, lobby,
                null, new Anniversaries(config.calendar(), null), messages, LoggerFactory.getLogger(LoginListenerTest.class));

        for (var game : List.of("bedwars", "knockbackffa")) {
            listener.onPostLogin(new PostLoginEvent(player));
            var backend = proxy.connect(player, game);
            proxy.runScheduled();

            assertEquals(backend, player.getCurrentServer().orElseThrow());
            assertTrue(proxy.requested(player).isEmpty());
        }
    }
}
