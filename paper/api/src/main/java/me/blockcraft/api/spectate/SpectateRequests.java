package me.blockcraft.api.spectate;

import com.google.common.io.ByteStreams;
import me.blockcraft.common.network.ProxyChannel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

/**
 * Whom an arriving player came to watch. Only the proxy knows that a jump to a friend ended on a server whose round
 * is already running, so it names the friend here and the games pick the event up.
 */
public final class SpectateRequests implements PluginMessageListener {

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        var in = ByteStreams.newDataInput(message);
        if (!channel.equals(ProxyChannel.NAME) || !in.readUTF().equals(ProxyChannel.SPECTATE)) {
            return;
        }
        new SpectateRequestEvent(player, UUID.fromString(in.readUTF())).callEvent();
    }
}
