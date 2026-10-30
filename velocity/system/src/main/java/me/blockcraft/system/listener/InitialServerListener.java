package me.blockcraft.system.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.network.Rejoins;

/**
 * Discovered servers are not in velocity.toml's {@code try} list, so the first server is chosen here: the one whose
 * running round still keeps the player's place, otherwise a lobby.
 */
public final class InitialServerListener {

    private final LobbyConnector lobby;
    private final Rejoins rejoins;

    public InitialServerListener(LobbyConnector lobby, Rejoins rejoins) {
        this.lobby = lobby;
        this.rejoins = rejoins;
    }

    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (event.getInitialServer().isPresent()) {
            return null;
        }
        return EventTask.async(() -> event.setInitialServer(
                rejoins.place(event.getPlayer().getUniqueId()).or(lobby::lobby).orElse(null)));
    }
}
