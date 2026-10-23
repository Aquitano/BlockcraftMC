package me.blockcraft.system;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.OperationCodec;
import me.blockcraft.common.network.operation.OperationResult;
import me.blockcraft.system.listener.OperationListener;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OperationListenerTest {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(OperationCodec.CHANNEL);
    private static final NetworkRequest REPORT = new NetworkRequest.SubmitReport("Target", "Spam");

    private final FakeProxy proxy = new FakeProxy();
    private final AtomicInteger executions = new AtomicInteger();
    private Messages messages;

    @Before
    public void loadMessages() throws Exception {
        messages = Messages.load(Files.createTempDirectory("operation-messages"), () -> OperationListenerTest.class.getResourceAsStream("/messages.yml"));
    }

    @Test
    public void executesOnceAndAnswersTheSameRequestIdFromMemory() throws Exception {
        var listener = listener((player, request) -> {
            executions.incrementAndGet();
            return OperationResult.APPLIED;
        });
        var player = proxy.join("Player");
        var backend = proxy.connect(player, "lobby");
        var id = UUID.randomUUID();

        deliver(listener, backend, id);
        deliver(listener, backend, id);

        assertEquals(1, executions.get());
        assertEquals(2, proxy.sentTo(backend).size());
        for (var reply : proxy.sentTo(backend)) {
            assertEquals(new OperationCodec.Reply(id, OperationResult.APPLIED), OperationCodec.reply(reply));
        }
    }

    @Test
    public void ignoresARequestFromAServerThePlayerHasLeft() throws Exception {
        var listener = listener((player, request) -> {
            executions.incrementAndGet();
            return OperationResult.APPLIED;
        });
        var player = proxy.join("Player");
        var old = proxy.connect(player, "lobby");
        proxy.connect(player, "quicksg");

        deliver(listener, old, UUID.randomUUID());

        assertEquals(0, executions.get());
        assertTrue(proxy.sentTo(old).isEmpty());
    }

    @Test
    public void aBrokenHandlerAnswersFailedAndTellsThePlayer() throws Exception {
        var listener = listener((player, request) -> {
            throw new IllegalStateException("boom");
        });
        var player = proxy.join("Player");
        var backend = proxy.connect(player, "lobby");
        var id = UUID.randomUUID();

        deliver(listener, backend, id);

        assertEquals(new OperationCodec.Reply(id, OperationResult.FAILED), OperationCodec.reply(proxy.sentTo(backend).getFirst()));
        assertEquals(1, proxy.received(player).size());
    }

    private OperationListener listener(OperationListener.Handler handler) {
        return new OperationListener(proxy.server, handler, messages, LoggerFactory.getLogger(OperationListenerTest.class));
    }

    private static void deliver(OperationListener listener, ServerConnection backend, UUID id) {
        var data = OperationCodec.encode(new OperationCodec.Request(id, REPORT));
        var task = listener.onMessage(new PluginMessageEvent(backend, backend.getPlayer(), CHANNEL, data));
        task.execute(new Continuation() {
            @Override
            public void resume() {
            }

            @Override
            public void resumeWithException(Throwable exception) {
                throw new AssertionError(exception);
            }
        });
    }
}
