package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.LobbyConnector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** A player kicked from a game server lands in the lobby with the reason instead of losing the connection. */
public final class KickListener {

    private final LobbyConnector lobby;
    private final Messages messages;

    public KickListener(LobbyConnector lobby, Messages messages) {
        this.lobby = lobby;
        this.messages = messages;
    }

    @Subscribe
    public void onKicked(KickedFromServerEvent event) {
        if (lobby.isLobby(event.getServer())) {
            return;
        }
        lobby.lobby().ifPresent(target -> event.setResult(RedirectPlayer.create(target, messages.get("kicked-to-lobby",
                Placeholder.unparsed("server", event.getServer().getServerInfo().getName()),
                Placeholder.component("reason", event.getServerKickReason().orElse(Component.empty()))))));
    }
}
