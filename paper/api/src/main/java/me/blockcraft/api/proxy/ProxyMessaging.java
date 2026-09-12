package me.blockcraft.api.proxy;

import com.google.common.io.ByteStreams;
import me.blockcraft.common.network.ProxyChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Plugin messages to the proxy: the BungeeCord channel Velocity answers itself, plus the Blockcraft channel. */
public final class ProxyMessaging {

    private static final String BUNGEE_CHANNEL = "BungeeCord";

    private ProxyMessaging() {
    }

    /** Moves the player to the named server. */
    public static void connect(Plugin plugin, Player player, String server) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        send(plugin, player, BUNGEE_CHANNEL, out.toByteArray());
    }

    /** Moves the player to whichever server of the group the proxy picks. */
    public static void play(Plugin plugin, Player player, String group) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF(ProxyChannel.PLAY);
        out.writeUTF(group);
        send(plugin, player, ProxyChannel.NAME, out.toByteArray());
    }

    /** Moves the player to a lobby. */
    public static void hub(Plugin plugin, Player player) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF(ProxyChannel.HUB);
        send(plugin, player, ProxyChannel.NAME, out.toByteArray());
    }

    /** Delivers the notice to every player on the network with the permission; {@code via} only carries the message. */
    public static void notifyStaff(Plugin plugin, Player via, String permission, Component notice) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF(ProxyChannel.STAFF_NOTICE);
        out.writeUTF(permission);
        out.writeUTF(GsonComponentSerializer.gson().serialize(notice));
        send(plugin, via, ProxyChannel.NAME, out.toByteArray());
    }

    private static void send(Plugin plugin, Player player, String channel, byte[] payload) {
        // Bukkit validates outgoing channels per sending plugin, so each caller is registered on first use.
        var messenger = plugin.getServer().getMessenger();
        if (!messenger.isOutgoingChannelRegistered(plugin, channel)) {
            messenger.registerOutgoingPluginChannel(plugin, channel);
        }
        player.sendPluginMessage(plugin, channel, payload);
    }
}
