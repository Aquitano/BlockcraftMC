package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.LobbyConnector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * A player kicked from a server lands in a lobby with the reason instead of losing the connection; a lobby that
 * dies hands its players to another lobby of the group, and only a network without a second lobby disconnects them.
 */
public final class KickListener {

    private final LobbyConnector lobby;
    private final Messages messages;

    public KickListener(LobbyConnector lobby, Messages messages) {
        this.lobby = lobby;
        this.messages = messages;
    }

    @Subscribe
    public void onKicked(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }
        var from = event.getServer();
        var target = lobby.isLobby(from) ? lobby.lobbyExcept(from) : lobby.lobby();
        target.ifPresent(server -> event.setResult(RedirectPlayer.create(server, messages.get("kicked-to-lobby",
                Placeholder.unparsed("server", from.getServerInfo().getName()),
                Placeholder.component("reason", event.getServerKickReason().orElse(Component.empty()))))));
    }
}
