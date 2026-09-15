package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.network.ServerDirectory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Optional;
import java.util.Set;

/** Sends players to the emptiest lobby of the configured lobby group. */
public final class LobbyConnector {

    private final ServerDirectory directory;
    private final SystemConfig config;
    private final Messages messages;

    public LobbyConnector(ServerDirectory directory, SystemConfig config, Messages messages) {
        this.directory = directory;
        this.config = config;
        this.messages = messages;
    }

    public Optional<RegisteredServer> lobby() {
        return directory.balance(config.lobbyGroup());
    }

    /** The crashed lobby can still be in the directory until its heartbeat goes stale, so it is excluded by name. */
    public Optional<RegisteredServer> lobbyExcept(RegisteredServer server) {
        return directory.balance(config.lobbyGroup(), Set.of(server.getServerInfo().getName()));
    }

    public boolean isLobby(RegisteredServer server) {
        return directory.groupOf(server.getServerInfo().getName()).map(config.lobbyGroup()::equals).orElse(false);
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

    /** Joins the fullest open server of a game group, which is what signs, /play and the navigator do. */
    public void play(Player player, String group) {
        String key = "play.games." + group;
        var game = Placeholder.component("game", messages.has(key) ? messages.get(key) : Component.text(group));
        var target = directory.fill(group);
        if (target.isEmpty()) {
            player.sendMessage(messages.get("play.unavailable", game));
            return;
        }
        if (player.getCurrentServer().map(connection -> connection.getServer().equals(target.get())).orElse(false)) {
            player.sendMessage(messages.get("play.already", game));
            return;
        }
        player.sendMessage(messages.get("play.connecting", game));
        player.createConnectionRequest(target.get()).fireAndForget();
    }
}
