package me.blockcraft.api.proxy;

import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** BungeeCord-channel plugin messages; Velocity answers them with its built-in BungeeCord compatibility. */
public final class ProxyMessaging {

    private static final String CHANNEL = "BungeeCord";

    private ProxyMessaging() {
    }

    public static void connect(Plugin plugin, Player player, String server) {
        // Bukkit validates outgoing channels per sending plugin, so each caller is registered on first use.
        var messenger = plugin.getServer().getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        var out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }
}
