package me.blockcraft.api.proxy;

import com.google.common.io.ByteStreams;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

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
        send(plugin, player, new ProxyMessage.Play(group));
    }

    /** Moves the player to a lobby. */
    public static void hub(Plugin plugin, Player player) {
        send(plugin, player, new ProxyMessage.Hub());
    }

    /** Runs a proxy command as the player, permission checks included; {@code command} carries no leading slash. */
    public static void runOnProxy(Plugin plugin, Player player, String command) {
        send(plugin, player, new ProxyMessage.Command(command));
    }

    /** Tells the proxy the nick the player wears from now on, or that it came off. */
    public static void nick(Plugin plugin, Player player, Optional<Nick> nick) {
        send(plugin, player, new ProxyMessage.NickChange(nick));
    }

    /** Tells the proxy what the player just chose, so private messages and invites follow the same setting. */
    public static void setting(Plugin plugin, Player player, PlayerSetting setting, String value) {
        send(plugin, player, new ProxyMessage.Setting(setting, value));
    }

    public static void vanish(Plugin plugin, Player player, boolean vanished) {
        send(plugin, player, new ProxyMessage.Vanish(vanished));
    }

    /** Tells the proxy the player went away from the keyboard or came back, so a private message can say so. */
    public static void afk(Plugin plugin, Player player, boolean afk) {
        send(plugin, player, new ProxyMessage.Afk(afk));
    }

    public static void requestPack(Plugin plugin, Player player) {
        send(plugin, player, new ProxyMessage.PackRequest());
    }

    /** Delivers the notice to every player on the network with the permission; {@code via} only carries the message. */
    public static void notifyStaff(Plugin plugin, Player via, String permission, Component notice) {
        send(plugin, via, new ProxyMessage.StaffNotice(permission, GsonComponentSerializer.gson().serialize(notice)));
    }

    public static void send(Plugin plugin, Player player, ProxyMessage.ToProxy message) {
        send(plugin, player, ProxyMessageCodec.CHANNEL, ProxyMessageCodec.encode(message));
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
