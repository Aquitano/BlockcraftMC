package me.blockcraft.system;

import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.listener.ClientVersionListener;
import net.kyori.adventure.text.Component;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientVersionListenerTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void olderClientsAreRejectedWithTheRequiredVersion() throws Exception {
        var listener = listener("26.3");
        for (var version : List.of(ProtocolVersion.MINECRAFT_1_8, ProtocolVersion.MINECRAFT_26_2)) {
            var event = event(version);
            listener.onPreLogin(event);
            assertFalse(event.getResult().isAllowed());
            assertEquals(Component.text("Minecraft 26.3 required"), event.getResult().getReasonComponent().orElseThrow());
        }
    }

    @Test
    public void theMinimumAndNewerKnownClientsCanContinue() throws Exception {
        var listener = listener("26.2");
        for (var version : List.of(ProtocolVersion.MINECRAFT_26_2, ProtocolVersion.MINECRAFT_26_3)) {
            var event = event(version);
            listener.onPreLogin(event);
            assertTrue(event.getResult().isAllowed());
        }
    }

    private ClientVersionListener listener(String version) throws Exception {
        var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream(
                "login:\n  version: 'Minecraft <version> required'\n".getBytes(StandardCharsets.UTF_8)));
        return new ClientVersionListener(version, messages);
    }

    private static PreLoginEvent event(ProtocolVersion version) {
        var connection = (InboundConnection) Proxy.newProxyInstance(InboundConnection.class.getClassLoader(),
                new Class<?>[]{InboundConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getProtocolVersion")) {
                        return version;
                    }
                    throw new AssertionError(method.getName());
                });
        return new PreLoginEvent(connection, "Steve", UUID.randomUUID());
    }
}
