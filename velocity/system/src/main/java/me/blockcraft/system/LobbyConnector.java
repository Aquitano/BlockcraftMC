package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.network.OnDemandServers;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Sends players to the emptiest lobby of the configured lobby group. */
public final class LobbyConnector {

    private final ServerDirectory directory;
    private final PartyManager parties;
    private final SystemConfig config;
    private final Messages messages;
    private final Optional<OnDemandServers> onDemand;

    /** {@code onDemand} starts a sleeping game's server for whoever wants in; empty while on-demand is off. */
    public LobbyConnector(ServerDirectory directory, PartyManager parties, SystemConfig config, Messages messages,
                          Optional<OnDemandServers> onDemand) {
        this.directory = directory;
        this.parties = parties;
        this.config = config;
        this.messages = messages;
        this.onDemand = onDemand;
    }

    /** The game's name for messages, its id where the messages lack one. */
    public TagResolver game(String group) {
        String key = "play.games." + group;
        return Placeholder.component("game", messages.has(key) ? messages.get(key) : Component.text(group));
    }

    public Optional<RegisteredServer> lobby() {
        return directory.balance(config.lobbyGroup());
    }

    /** A lobby that crashed or refused stays in the directory until its heartbeat goes stale, so it is excluded by name. */
    public Optional<RegisteredServer> lobbyExcept(Set<String> servers) {
        return directory.balance(config.lobbyGroup(), servers);
    }

    public boolean isLobby(RegisteredServer server) {
        return directory.groupOf(server.getServerInfo().getName()).map(config.lobbyGroup()::equals).orElse(false);
    }

    public boolean isOnLobby(Player player) {
        return player.getCurrentServer().map(connection -> isLobby(connection.getServer())).orElse(false);
    }

    public void send(Player player) {
        if (isOnLobby(player)) {
            player.sendMessage(messages.get("hub.already"));
            return;
        }
        var lobby = lobby();
        if (lobby.isEmpty()) {
            player.sendMessage(messages.get("hub.unavailable"));
            return;
        }
        player.sendMessage(messages.get("hub.connecting"));
        directory.send(player, lobby.get());
    }

    /**
     * Joins the fullest open server of a game group, which is what signs, /play and the navigator do. A party leader
     * takes the whole party along, so the server needs a seat for every member who will follow. The lobby group is
     * spread like logins instead.
     */
    public void play(Player player, String group) {
        group = group.toLowerCase(Locale.ROOT);
        if (group.equals(config.lobbyGroup())) {
            send(player);
            return;
        }
        var game = game(group);
        var seated = Stream.concat(Stream.of(player.getUniqueId()), parties.followersOf(player.getUniqueId()).stream()).toList();
        var target = directory.fill(group, seated);
        if (target.isEmpty()) {
            String sleeping = group;
            // Play again from the round that just ended: its server restarts before it has room, so wait for it.
            boolean playedHere = player.getCurrentServer().flatMap(connection -> directory.groupOf(connection.getServerInfo().getName()))
                    .filter(sleeping::equals).isPresent();
            if (onDemand.filter(servers -> playedHere ? servers.rejoin(player, sleeping, game) : servers.request(player, sleeping, game)).isPresent()) {
                return;
            }
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
        var game = game(group.get());
        var pair = List.of(player, opponent);
        var target = directory.fill(group.get(), pair.stream().map(Player::getUniqueId).toList());
        if (target.isEmpty()) {
            // The only instance is restarting after the round they just played: on demand, both wait for it to come back.
            boolean waiting = onDemand.filter(servers -> pair.stream().allMatch(each -> servers.rejoin(each, group.get(), game))).isPresent();
            if (!waiting) {
                pair.forEach(each -> each.sendMessage(messages.get("play.rematch-unavailable", game)));
            }
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
