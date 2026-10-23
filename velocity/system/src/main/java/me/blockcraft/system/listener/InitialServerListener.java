package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import me.blockcraft.system.LobbyConnector;

/** Discovered servers are not in velocity.toml's {@code try} list, so the lobby is chosen here. */
public final class InitialServerListener {

    private final LobbyConnector lobby;

    public InitialServerListener(LobbyConnector lobby) {
        this.lobby = lobby;
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (event.getInitialServer().isEmpty()) {
            event.setInitialServer(lobby.lobby().orElse(null));
        }
    }
}
