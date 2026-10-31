package me.blockcraft.system.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.OperationCodec;
import me.blockcraft.common.network.operation.OperationResult;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Executes the typed requests a backend sends on a player's behalf and answers with the result. A request is
 * accepted only from the server the player is on right now, and a repeated request id gets the recorded answer
 * instead of a second execution. The handler tells the player what happened; this class only speaks when the
 * handler itself broke.
 */
public final class OperationListener {

    @FunctionalInterface
    public interface Handler {
        OperationResult execute(Player player, NetworkRequest request);
    }

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(OperationCodec.CHANNEL);
    private static final int REMEMBERED_RESULTS = 10_000;
    private static final Duration RESULT_MEMORY = Duration.ofMinutes(1);

    private record Key(UUID player, UUID request) {
    }

    private final Cache<Key, OperationResult> completed = CacheBuilder.newBuilder()
            .maximumSize(REMEMBERED_RESULTS)
            .expireAfterWrite(RESULT_MEMORY)
            .build();
    private final Handler handler;
    private final Messages messages;
    private final Logger logger;

    public OperationListener(ProxyServer proxy, Handler handler, Messages messages, Logger logger) {
        this.handler = handler;
        this.messages = messages;
        this.logger = logger;
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public EventTask onMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return null;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return null;
        }
        OperationCodec.Request request;
        try {
            request = OperationCodec.request(event.getData());
        } catch (IOException e) {
            logger.warn("Invalid operation from {}: {}", connection.getServerInfo().getName(), e.getMessage());
            return null;
        }
        return EventTask.async(() -> {
            var result = execute(connection, request);
            if (isCurrent(connection)) {
                connection.sendPluginMessage(CHANNEL, OperationCodec.encode(new OperationCodec.Reply(request.id(), result)));
            }
        });
    }

    /** Serialized so a duplicate cannot run while its first execution is still changing storage. */
    private synchronized OperationResult execute(ServerConnection connection, OperationCodec.Request request) {
        var player = connection.getPlayer();
        if (!player.isActive() || !isCurrent(connection)) {
            return OperationResult.REJECTED;
        }
        var key = new Key(player.getUniqueId(), request.id());
        var remembered = completed.getIfPresent(key);
        if (remembered != null) {
            return remembered;
        }
        OperationResult result;
        try {
            result = handler.execute(player, request.operation());
        } catch (RuntimeException e) {
            logger.error("Network operation {} for {} failed", request.id(), player.getUniqueId(), e);
            player.sendMessage(messages.get("request-failed"));
            result = OperationResult.FAILED;
        }
        completed.put(key, result);
        return result;
    }

    private static boolean isCurrent(ServerConnection connection) {
        return connection.getPlayer().getCurrentServer().filter(connection::equals).isPresent();
    }
}
