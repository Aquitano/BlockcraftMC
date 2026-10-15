package me.blockcraft.api.proxy;

import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.OperationCodec;
import me.blockcraft.common.network.operation.OperationResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Typed requests to the proxy over {@link OperationCodec#CHANNEL}: reports and punishments go out as data, the proxy
 * applies its own permission and rank checks and answers with a result. One request per player is in flight at a
 * time; the reply is matched by id, so a late answer cannot reach a later request or another session.
 */
public final class NetworkOperations implements PluginMessageListener, Listener {

    private static final long TIMEOUT_TICKS = 200;

    private record Pending(UUID id, Player player, BukkitTask timeout, Consumer<OperationResult> completion) {
    }

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Plugin plugin;
    private final Messages messages;

    public NetworkOperations(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, OperationCodec.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, OperationCodec.CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void submit(Player player, NetworkRequest operation) {
        submit(player, operation, result -> {
        });
    }

    /** The proxy answers the player itself; {@code completion} is for what the backend does afterwards. */
    public void submit(Player player, NetworkRequest operation, Consumer<OperationResult> completion) {
        if (!player.isOnline()) {
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(messages.get("operation.pending"));
            return;
        }
        var id = UUID.randomUUID();
        var timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> finish(player, id, OperationResult.TIMED_OUT), TIMEOUT_TICKS);
        pending.put(player.getUniqueId(), new Pending(id, player, timeout, completion));
        player.sendPluginMessage(plugin, OperationCodec.CHANNEL, OperationCodec.encode(new OperationCodec.Request(id, operation)));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!OperationCodec.CHANNEL.equals(channel)) {
            return;
        }
        try {
            var reply = OperationCodec.reply(message);
            finish(player, reply.id(), reply.result());
        } catch (IOException e) {
            plugin.getLogger().warning("Invalid operation reply: " + e.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var operation = pending.remove(event.getPlayer().getUniqueId());
        if (operation != null) {
            operation.timeout().cancel();
        }
    }

    /** The proxy answers every request it saw; only an unanswered one is unconfirmed, and it is never retried because the proxy may have applied it. */
    private void finish(Player player, UUID id, OperationResult result) {
        var operation = pending.get(player.getUniqueId());
        if (operation == null || operation.player() != player || !operation.id().equals(id)) {
            return;
        }
        pending.remove(player.getUniqueId());
        operation.timeout().cancel();
        if (!player.isOnline()) {
            return;
        }
        if (result == OperationResult.TIMED_OUT) {
            player.sendMessage(messages.get("operation.unconfirmed"));
        }
        operation.completion().accept(result);
    }
}
