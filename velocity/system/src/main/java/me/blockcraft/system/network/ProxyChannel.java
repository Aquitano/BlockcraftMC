package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;

/** The Blockcraft plugin channel as the proxy speaks it to the backends. */
public final class ProxyChannel {

    public static final MinecraftChannelIdentifier ID = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private ProxyChannel() {
    }

    /** Dropped while the player is between servers. */
    public static void send(Player player, ProxyMessage.ToBackend message) {
        player.getCurrentServer().ifPresent(connection -> send(connection, message));
    }

    public static void send(ChannelMessageSink backend, ProxyMessage.ToBackend message) {
        backend.sendPluginMessage(ID, ProxyMessageCodec.encode(message));
    }
}
