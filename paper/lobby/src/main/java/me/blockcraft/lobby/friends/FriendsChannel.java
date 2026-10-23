package me.blockcraft.lobby.friends;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.network.ProxyMessage.FriendsReply;
import me.blockcraft.common.network.ProxyMessage.FriendsRequest;
import me.blockcraft.common.network.ProxyMessageCodec;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class FriendsChannel implements PluginMessageListener, Listener {
    private record Pending(UUID id, BooleanSupplier current, Consumer<FriendsReply> complete, BukkitTask timeout) {}

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Plugin plugin;
    private final long timeoutTicks;

    public FriendsChannel(Plugin plugin, long timeoutTicks) {
        this.plugin = plugin;
        this.timeoutTicks = timeoutTicks;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, ProxyMessageCodec.CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void request(Player player, FriendsRequest.Action action, String name, Consumer<FriendsReply> complete) {
        cancel(player.getUniqueId());
        var id = UUID.randomUUID();
        var current = BlockcraftApi.get().playerTasks().currentScreen(player);
        var timeout = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var request = pending.remove(player.getUniqueId());
            if (request != null && request.current().getAsBoolean()) {
                complete.accept(new FriendsReply(id, FriendsReply.Status.FAILED, List.of(), List.of()));
            }
        }, timeoutTicks);
        pending.put(player.getUniqueId(), new Pending(id, current, complete, timeout));
        ProxyMessaging.send(plugin, player, new FriendsRequest(id, action, name));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(ProxyMessageCodec.CHANNEL)) {
            return;
        }
        ProxyMessageCodec.decode(message, FriendsReply.class).ifPresent(reply -> answer(player, reply));
    }

    private void answer(Player player, FriendsReply reply) {
        var request = pending.get(player.getUniqueId());
        if (request == null || !request.id().equals(reply.id())) {
            return;
        }
        cancel(player.getUniqueId());
        if (request.current().getAsBoolean()) {
            request.complete().accept(reply);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    public void close() {
        pending.values().forEach(request -> request.timeout().cancel());
        pending.clear();
    }

    private void cancel(UUID player) {
        var request = pending.remove(player);
        if (request != null) {
            request.timeout().cancel();
        }
    }
}
