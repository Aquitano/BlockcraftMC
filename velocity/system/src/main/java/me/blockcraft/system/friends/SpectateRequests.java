package me.blockcraft.system.friends;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whom a player came to watch. The connection itself says nothing about the reason, so the proxy remembers the
 * friend from the jump until the player arrives on their server and tells the backend there, which is the only
 * moment a game can put the arriving spectator next to them.
 */
public final class SpectateRequests {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private record Intent(UUID friend, String server) {
    }

    private final Map<UUID, Intent> intents = new ConcurrentHashMap<>();

    void watch(Player player, UUID friend, String server) {
        intents.put(player.getUniqueId(), new Intent(friend, server));
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        var intent = intents.remove(player.getUniqueId());
        if (intent == null) {
            return;
        }
        player.getCurrentServer()
                .filter(connection -> connection.getServerInfo().getName().equals(intent.server()))
                .ifPresent(connection -> tell(connection, intent.friend()));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        intents.remove(event.getPlayer().getUniqueId());
    }

    private static void tell(ServerConnection connection, UUID friend) {
        connection.sendPluginMessage(CHANNEL, ProxyMessageCodec.encode(new ProxyMessage.Spectate(friend)));
    }
}
