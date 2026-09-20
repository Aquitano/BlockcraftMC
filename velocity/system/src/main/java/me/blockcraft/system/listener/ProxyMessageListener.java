package me.blockcraft.system.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.FriendsMessage;
import me.blockcraft.common.network.PackStatus;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.friends.FriendManager;
import me.blockcraft.system.pack.NetworkPack;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
    private final Supplier<Optional<NetworkPack>> pack;

    public ProxyMessageListener(ProxyServer proxy, LobbyConnector lobby, PlayerVisibility visibility, PlayerChoices choices,
                                FriendManager friends, TabHeaderListener tabHeader, Logger logger, Supplier<Optional<NetworkPack>> pack) {
        this.proxy = proxy;
        this.lobby = lobby;
        this.visibility = visibility;
        this.choices = choices;
        this.friends = friends;
        this.tabHeader = tabHeader;
        this.logger = logger;
        this.pack = pack;
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
            case ProxyChannel.PACK_REQUEST -> pack.get().ifPresentOrElse(networkPack -> networkPack.retry(player),
                    () -> NetworkPack.tell(player, PackStatus.UNAVAILABLE));
            case ProxyChannel.PLAY -> lobby.play(player, in.readUTF());
            case ProxyChannel.HUB -> lobby.send(player);
            case ProxyChannel.COMMAND -> proxy.getCommandManager().executeAsync(player, in.readUTF());
            case ProxyChannel.NICK -> {
                var nick = new Nick(in.readUTF(), in.readUTF(), in.readUTF());
                visibility.setNick(player, Optional.of(nick).filter(worn -> !worn.name().isEmpty()));
            }
            // The friend list is a database read, so it leaves the network thread.
            case ProxyChannel.FRIENDS -> {
                return EventTask.async(() -> sendFriends(connection, player, event.getData()));
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

    private void sendFriends(ServerConnection connection, Player player, byte[] payload) {
        FriendsMessage.Request request;
        try {
            request = FriendsMessage.request(payload);
        } catch (IOException | IllegalArgumentException e) {
            logger.warn("Invalid friends request from {}", connection.getServerInfo().getName());
            return;
        }
        if (!player.hasPermission(SystemPermissions.FRIENDS)) {
            reply(connection, new FriendsMessage.Reply(request.id(), FriendsMessage.Status.FORBIDDEN, List.of(), List.of()));
            return;
        }
        try {
            switch (request.action()) {
                case LIST -> { }
                case ADD -> friends.add(player, request.name());
                case ACCEPT -> friends.accept(player, request.name());
                case DENY -> friends.deny(player, request.name());
            }
            var entries = friends.entriesFor(player).stream()
                    .map(entry -> new FriendsMessage.Entry(entry.name(), entry.online(), entry.server(), entry.watching())).toList();
            reply(connection, new FriendsMessage.Reply(request.id(), FriendsMessage.Status.OK, entries, friends.requestNames(player)));
        } catch (DatabaseException | IllegalArgumentException e) {
            logger.warn("Could not load friends for {}", player.getUsername(), e);
            reply(connection, new FriendsMessage.Reply(request.id(), FriendsMessage.Status.FAILED, List.of(), List.of()));
        }
    }

    private void reply(ServerConnection connection, FriendsMessage.Reply reply) {
        connection.sendPluginMessage(CHANNEL, FriendsMessage.encode(reply));
    }
}
