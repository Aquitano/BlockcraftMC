package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.LobbyConnector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player kicked from a server lands in a lobby with the reason instead of losing the connection; a lobby that
 * dies hands its players to another lobby of the group, and only a network without a second lobby disconnects them.
 * A failed switch leaves the player where they are, but a login has nowhere to stay, so it tries the next lobby.
 * Every server that turned a player away is skipped until they arrive somewhere, so lobbies that all refuse end the
 * connection instead of passing it back and forth.
 */
public final class KickListener {

    private final LobbyConnector lobby;
    private final Messages messages;
    private final Map<UUID, Set<String>> refused = new ConcurrentHashMap<>();

    public KickListener(LobbyConnector lobby, Messages messages) {
        this.lobby = lobby;
        this.messages = messages;
    }

    @Subscribe
    public void onKicked(KickedFromServerEvent event) {
        var player = event.getPlayer();
        if (event.kickedDuringServerConnect() && player.getCurrentServer().isPresent()) {
            return;
        }
        var from = event.getServer();
        var skipped = refused.computeIfAbsent(player.getUniqueId(), _ -> ConcurrentHashMap.newKeySet());
        skipped.add(from.getServerInfo().getName());
        lobby.lobbyExcept(skipped).ifPresent(server -> event.setResult(RedirectPlayer.create(server, messages.get("kicked-to-lobby",
                Placeholder.unparsed("server", from.getServerInfo().getName()),
                Placeholder.component("reason", event.getServerKickReason().orElse(Component.empty()))))));
    }

    @Subscribe
    public void onArrived(ServerPostConnectEvent event) {
        refused.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        refused.remove(event.getPlayer().getUniqueId());
    }
}
