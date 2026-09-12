package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;

import java.util.Optional;

public final class LobbyConnector {

    private final ProxyServer proxy;
    private final SystemConfig config;
    private final Messages messages;

    public LobbyConnector(ProxyServer proxy, SystemConfig config, Messages messages) {
        this.proxy = proxy;
        this.config = config;
        this.messages = messages;
    }

    public Optional<RegisteredServer> lobby() {
        return proxy.getServer(config.lobbyServer());
    }

    public boolean isLobby(RegisteredServer server) {
        return server.getServerInfo().getName().equals(config.lobbyServer());
    }

    public boolean isOnLobby(Player player) {
        return player.getCurrentServer().map(connection -> isLobby(connection.getServer())).orElse(false);
    }

    public void send(Player player) {
        var lobby = lobby();
        if (lobby.isEmpty()) {
            player.sendMessage(messages.get("hub.unavailable"));
            return;
        }
        if (isOnLobby(player)) {
            player.sendMessage(messages.get("hub.already"));
            return;
        }
        player.sendMessage(messages.get("hub.connecting"));
        player.createConnectionRequest(lobby.get()).fireAndForget();
    }
}
