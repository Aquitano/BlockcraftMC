package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Sends players to the emptiest lobby of the configured lobby group. */
public final class LobbyConnector {

    private final ServerDirectory directory;
    private final PartyManager parties;
    private final SystemConfig config;
    private final Messages messages;

    public LobbyConnector(ServerDirectory directory, PartyManager parties, SystemConfig config, Messages messages) {
        this.directory = directory;
        this.parties = parties;
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

    /**
     * Joins the fullest open server of a game group, which is what signs, /play and the navigator do. A party leader
     * takes the whole party along, so the server needs a seat for every member who will follow.
     */
    public void play(Player player, String group) {
        String key = "play.games." + group;
        var game = Placeholder.component("game", messages.has(key) ? messages.get(key) : Component.text(group));
        var seated = Stream.concat(Stream.of(player.getUniqueId()), parties.followersOf(player.getUniqueId()).stream()).toList();
        var target = directory.fill(group, seated);
        if (target.isEmpty()) {
            player.sendMessage(seated.size() > 1
                    ? messages.get("play.no-room-for-party", game, Formatter.number("count", seated.size()))
                    : messages.get("play.unavailable", game));
            return;
        }
        String server = target.get().getServerInfo().getName();
        if (player.getCurrentServer().map(connection -> connection.getServer().equals(target.get())).orElse(false)) {
            seated.forEach(id -> directory.release(id, server));
            player.sendMessage(messages.get("play.already", game));
            return;
        }
        player.sendMessage(messages.get("play.connecting", game));
        directory.send(player, target.get()).thenAccept(connected -> {
            if (!connected) {
                seated.forEach(id -> directory.release(id, server));
            }
        });
    }

    /**
     * Seats two players who just played each other on the same server together in a fresh round of that server's
     * group; a server elsewhere, or none with two free seats, leaves both where they are.
     */
    public void rematch(Player player, Player opponent) {
        var here = player.getCurrentServer().map(connection -> connection.getServer().getServerInfo().getName());
        var there = opponent.getCurrentServer().map(connection -> connection.getServer().getServerInfo().getName());
        var group = here.filter(there.orElse("")::equals).flatMap(directory::groupOf);
        if (group.isEmpty()) {
            return;
        }
        String key = "play.games." + group.get();
        var game = Placeholder.component("game", messages.has(key) ? messages.get(key) : Component.text(group.get()));
        var pair = List.of(player, opponent);
        var target = directory.fill(group.get(), pair.stream().map(Player::getUniqueId).toList());
        if (target.isEmpty()) {
            pair.forEach(each -> each.sendMessage(messages.get("play.rematch-unavailable", game)));
            return;
        }
        String server = target.get().getServerInfo().getName();
        for (var each : pair) {
            each.sendMessage(messages.get("play.rematch", game));
            directory.send(each, target.get()).thenAccept(connected -> {
                if (!connected) {
                    directory.release(each.getUniqueId(), server);
                }
            });
        }
    }
}
