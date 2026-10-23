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
import me.blockcraft.common.network.PackStatus;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessage.FriendsReply;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.system.AfkPlayers;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.friends.FriendManager;
import me.blockcraft.system.pack.NetworkPack;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Answers the backends' requests on the Blockcraft channel; the player the message travelled with is the subject. */
public final class ProxyMessageListener {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private final ProxyServer proxy;
    private final LobbyConnector lobby;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final AfkPlayers afk;
    private final FriendManager friends;
    private final TabHeaderListener tabHeader;
    private final Logger logger;
    private final Supplier<Optional<NetworkPack>> pack;

    public ProxyMessageListener(ProxyServer proxy, LobbyConnector lobby, PlayerVisibility visibility, PlayerChoices choices,
                                AfkPlayers afk, FriendManager friends, TabHeaderListener tabHeader, Logger logger, Supplier<Optional<NetworkPack>> pack) {
        this.proxy = proxy;
        this.lobby = lobby;
        this.visibility = visibility;
        this.choices = choices;
        this.afk = afk;
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
        var message = ProxyMessageCodec.decode(event.getData(), ProxyMessage.ToProxy.class);
        if (message.isEmpty()) {
            logger.warn("Dropped an unreadable message on {} from {}", ProxyMessageCodec.CHANNEL, connection.getServerInfo().getName());
            return null;
        }
        switch (message.get()) {
            case ProxyMessage.PackRequest _ -> pack.get().ifPresentOrElse(networkPack -> networkPack.retry(player),
                    () -> NetworkPack.tell(player, PackStatus.UNAVAILABLE));
            case ProxyMessage.Play play -> lobby.play(player, play.group());
            case ProxyMessage.Hub _ -> lobby.send(player);
            case ProxyMessage.Command command -> proxy.getCommandManager().executeAsync(player, command.line());
            case ProxyMessage.NickChange change -> visibility.setNick(player, change.nick());
            // The friend list is a database read, so it leaves the network thread.
            case ProxyMessage.FriendsRequest request -> {
                return EventTask.async(() -> sendFriends(connection, player, request));
            }
            case ProxyMessage.Setting setting -> choices.set(player, setting.setting(), setting.value());
            case ProxyMessage.Afk away -> afk.set(player, away.afk());
            case ProxyMessage.Vanish vanish -> {
                visibility.setVanished(player, vanish.vanished());
                tabHeader.refreshAll();
            }
            case ProxyMessage.Announcement announcement -> {
                var line = GsonComponentSerializer.gson().deserialize(announcement.noticeJson());
                proxy.getAllPlayers().forEach(online -> online.sendMessage(line));
            }
            case ProxyMessage.Rematch rematch -> proxy.getPlayer(rematch.opponent())
                    .ifPresent(opponent -> lobby.rematch(player, opponent));
            case ProxyMessage.StaffNotice staffNotice -> {
                var notice = GsonComponentSerializer.gson().deserialize(staffNotice.noticeJson());
                for (Player online : proxy.getAllPlayers()) {
                    if (online.hasPermission(staffNotice.permission())) {
                        online.sendMessage(notice);
                    }
                }
            }
        }
        return null;
    }

    private void sendFriends(ServerConnection connection, Player player, ProxyMessage.FriendsRequest request) {
        if (!player.hasPermission(SystemPermissions.FRIENDS)) {
            reply(connection, new FriendsReply(request.id(), FriendsReply.Status.FORBIDDEN, List.of(), List.of()));
            return;
        }
        try {
            switch (request.action()) {
                case LIST -> { }
                case ADD -> friends.add(player, request.name());
                case ACCEPT -> friends.accept(player, request.name());
                case DENY -> friends.deny(player, request.name());
            }
            reply(connection, new FriendsReply(request.id(), FriendsReply.Status.OK, friends.entriesFor(player),
                    friends.requestNames(player)));
        } catch (DatabaseException | IllegalArgumentException e) {
            logger.warn("Could not load friends for {}", player.getUsername(), e);
            reply(connection, new FriendsReply(request.id(), FriendsReply.Status.FAILED, List.of(), List.of()));
        }
    }

    private void reply(ServerConnection connection, FriendsReply reply) {
        connection.sendPluginMessage(CHANNEL, ProxyMessageCodec.encode(reply));
    }
}
