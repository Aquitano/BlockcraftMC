package me.blockcraft.system.listener;

import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.friends.FriendManager;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/** Answers the backends' requests on the Blockcraft channel; the player the message travelled with is the subject. */
public final class ProxyMessageListener {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyChannel.NAME);

    private final ProxyServer proxy;
    private final LobbyConnector lobby;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final FriendManager friends;
    private final TabHeaderListener tabHeader;
    private final Logger logger;

    public ProxyMessageListener(ProxyServer proxy, LobbyConnector lobby, PlayerVisibility visibility, PlayerChoices choices,
                                FriendManager friends, TabHeaderListener tabHeader, Logger logger) {
        this.proxy = proxy;
        this.lobby = lobby;
        this.visibility = visibility;
        this.choices = choices;
        this.friends = friends;
        this.tabHeader = tabHeader;
        this.logger = logger;
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public EventTask onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return null;
        }
        event.setResult(ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return null;
        }
        var player = connection.getPlayer();
        var in = event.dataAsDataStream();
        String subchannel = in.readUTF();
        switch (subchannel) {
            case ProxyChannel.PLAY -> lobby.play(player, in.readUTF());
            case ProxyChannel.HUB -> lobby.send(player);
            case ProxyChannel.COMMAND -> proxy.getCommandManager().executeAsync(player, in.readUTF());
            case ProxyChannel.NICK -> visibility.setNick(player, Optional.of(in.readUTF()).filter(nick -> !nick.isEmpty()));
            // The friend list is a database read, so it leaves the network thread.
            case ProxyChannel.FRIENDS -> {
                return EventTask.async(() -> sendFriends(connection, player));
            }
            case ProxyChannel.SETTING -> {
                String key = in.readUTF();
                String value = in.readUTF();
                PlayerSetting.byKey(key).ifPresent(setting -> choices.set(player, setting, value));
            }
            case ProxyChannel.VANISH -> {
                visibility.setVanished(player, in.readBoolean());
                tabHeader.refreshAll();
            }
            case ProxyChannel.STAFF_NOTICE -> {
                String permission = in.readUTF();
                var notice = GsonComponentSerializer.gson().deserialize(in.readUTF());
                for (Player online : proxy.getAllPlayers()) {
                    if (online.hasPermission(permission)) {
                        online.sendMessage(notice);
                    }
                }
            }
            default -> logger.warn("Unknown subchannel {} from {}", subchannel, connection.getServerInfo().getName());
        }
        return null;
    }

    /** Only the proxy knows who is online and where, so the lobby's friends menu is drawn from this answer. */
    private void sendFriends(ServerConnection connection, Player player) {
        try {
            sendFriends(connection, friends.entriesFor(player), friends.requestNames(player));
        } catch (DatabaseException e) {
            logger.warn("Could not read the friends of {}", player.getUsername(), e);
            sendFriends(connection, List.of(), List.of());
        }
    }

    /** An empty answer is what the menu draws when the database did not answer; a missing one would hang it. */
    private void sendFriends(ServerConnection connection, List<FriendManager.Entry> entries, List<String> requests) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF(ProxyChannel.FRIENDS);
        out.writeInt(entries.size());
        for (var entry : entries) {
            out.writeUTF(entry.name());
            out.writeBoolean(entry.online());
            out.writeUTF(entry.server());
        }
        out.writeInt(requests.size());
        requests.forEach(out::writeUTF);
        connection.sendPluginMessage(CHANNEL, out.toByteArray());
    }
}
