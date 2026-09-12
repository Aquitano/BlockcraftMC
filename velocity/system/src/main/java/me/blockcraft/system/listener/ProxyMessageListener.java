package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.system.LobbyConnector;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

/** Answers the backends' requests on the Blockcraft channel; the player the message travelled with is the subject. */
public final class ProxyMessageListener {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyChannel.NAME);

    private final ProxyServer proxy;
    private final LobbyConnector lobby;
    private final Logger logger;

    public ProxyMessageListener(ProxyServer proxy, LobbyConnector lobby, Logger logger) {
        this.proxy = proxy;
        this.lobby = lobby;
        this.logger = logger;
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        var in = event.dataAsDataStream();
        String subchannel = in.readUTF();
        switch (subchannel) {
            case ProxyChannel.PLAY -> lobby.play(connection.getPlayer(), in.readUTF());
            case ProxyChannel.HUB -> lobby.send(connection.getPlayer());
            case ProxyChannel.STAFF_NOTICE -> {
                String permission = in.readUTF();
                var notice = GsonComponentSerializer.gson().deserialize(in.readUTF());
                for (Player player : proxy.getAllPlayers()) {
                    if (player.hasPermission(permission)) {
                        player.sendMessage(notice);
                    }
                }
            }
            default -> logger.warn("Unknown subchannel {} from {}", subchannel, connection.getServerInfo().getName());
        }
    }
}
