package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.player.PlayerReference;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.moderation.PlayerRepository;

import java.util.Optional;
import java.util.UUID;

/** Resolves a typed name, nicks included, to a player that is online now or has been seen by the proxy before. */
public final class PlayerLookup {

    public record Target(UUID uuid, String name, Optional<Player> online) {
    }

    private final ProxyServer proxy;
    private final PlayerVisibility visibility;
    private final PlayerRepository players;

    public PlayerLookup(ProxyServer proxy, PlayerVisibility visibility, PlayerRepository players) {
        this.proxy = proxy;
        this.visibility = visibility;
        this.players = players;
    }

    public Optional<Target> find(String name) {
        return proxy.getPlayer(name).or(() -> visibility.byNick(name))
                .map(player -> new Target(player.getUniqueId(), player.getUsername(), Optional.of(player)))
                .or(() -> players.byName(name).map(known -> new Target(known.uuid(), known.name(), Optional.empty())));
    }

    /** Visibility applies before offline fallback, so hidden online accounts cannot be recovered by name. */
    public Optional<Target> findVisible(CommandSource viewer, String name) {
        var online = visibility.find(viewer, name);
        if (online.isPresent()) {
            var player = online.get();
            return Optional.of(new Target(player.getUniqueId(), player.getUsername(), online));
        }
        if (proxy.getPlayer(name).isPresent() || visibility.byNick(name).isPresent()) return Optional.empty();
        return players.byName(name).filter(known -> proxy.getPlayer(known.uuid()).isEmpty())
                .map(known -> new Target(known.uuid(), known.name(), Optional.empty()));
    }

    public Optional<Target> find(PlayerReference reference) {
        return reference.uuid().map(uuid -> proxy.getPlayer(uuid)
                .map(player -> new Target(uuid, player.getUsername(), Optional.of(player)))
                .or(() -> players.byUuid(uuid).map(known -> new Target(uuid, known.name(), Optional.empty()))))
                .orElseGet(() -> find(reference.name()));
    }

    public Optional<String> nameOf(UUID uuid) {
        return proxy.getPlayer(uuid).map(Player::getUsername).or(() -> players.byUuid(uuid).map(known -> known.name()));
    }
}
