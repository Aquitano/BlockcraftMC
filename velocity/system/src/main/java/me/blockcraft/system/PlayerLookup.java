package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.moderation.PlayerRepository;

import java.util.Optional;
import java.util.UUID;

/** Resolves a typed name to a player that is online now or has been seen by the proxy before. */
public final class PlayerLookup {

    public record Target(UUID uuid, String name, Optional<Player> online) {
    }

    private final ProxyServer proxy;
    private final PlayerRepository players;

    public PlayerLookup(ProxyServer proxy, PlayerRepository players) {
        this.proxy = proxy;
        this.players = players;
    }

    public Optional<Target> find(String name) {
        return proxy.getPlayer(name)
                .map(player -> new Target(player.getUniqueId(), player.getUsername(), Optional.of(player)))
                .or(() -> players.byName(name).map(known -> new Target(known.uuid(), known.name(), Optional.empty())));
    }

    public Optional<String> nameOf(UUID uuid) {
        return proxy.getPlayer(uuid).map(Player::getUsername).or(() -> players.byUuid(uuid).map(known -> known.name()));
    }
}
