package me.blockcraft.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.command.CommandSource;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.player.PlayerReference;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.moderation.PlayerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resolves a typed name, nicks included, to a player that is online now or has been seen by the proxy before. A name
 * that more than one online player goes by is refused rather than guessed, and the asker hears why nothing was found,
 * so every caller can act on a hit alone.
 */
public final class PlayerLookup {

    public record Target(UUID uuid, String name, Optional<Player> online) {

        private static Target of(Player player) {
            return new Target(player.getUniqueId(), player.getUsername(), Optional.of(player));
        }
    }

    private final ProxyServer proxy;
    private final PlayerVisibility visibility;
    private final PlayerRepository players;
    private final Messages messages;

    public PlayerLookup(ProxyServer proxy, PlayerVisibility visibility, PlayerRepository players, Messages messages) {
        this.proxy = proxy;
        this.visibility = visibility;
        this.players = players;
        this.messages = messages;
    }

    /** For staff tools: real names and nicks alike, vanished players included. */
    public Optional<Target> find(CommandSource asker, String name, Component unknown) {
        return resolve(asker, name, visibility.named(name).toList(),
                () -> players.byName(name).map(known -> new Target(known.uuid(), known.name(), Optional.empty())), unknown);
    }

    /** Visibility applies before offline fallback, so hidden online accounts cannot be recovered by name. */
    public Optional<Target> findVisible(CommandSource asker, String name, Component unknown) {
        return resolve(asker, name, visibility.matches(asker, name), () -> visibility.named(name).findAny().isPresent()
                ? Optional.empty()
                : players.byName(name).filter(known -> proxy.getPlayer(known.uuid()).isEmpty())
                        .map(known -> new Target(known.uuid(), known.name(), Optional.empty())), unknown);
    }

    /** The online player the asker means, as far as they may see them. */
    public Optional<Player> online(CommandSource asker, String name, Component unknown) {
        return resolve(asker, name, visibility.matches(asker, name), Optional::empty, unknown).flatMap(Target::online);
    }

    public Optional<Target> find(CommandSource asker, PlayerReference reference, Component unknown) {
        if (reference.uuid().isEmpty()) {
            return find(asker, reference.name(), unknown);
        }
        UUID uuid = reference.uuid().get();
        var target = proxy.getPlayer(uuid).map(Target::of)
                .or(() -> players.byUuid(uuid).map(known -> new Target(uuid, known.name(), Optional.empty())));
        if (target.isEmpty()) {
            asker.sendMessage(unknown);
        }
        return target;
    }

    public Optional<String> nameOf(UUID uuid) {
        return proxy.getPlayer(uuid).map(Player::getUsername).or(() -> players.byUuid(uuid).map(known -> known.name()));
    }

    private Optional<Target> resolve(CommandSource asker, String name, List<Player> online, Supplier<Optional<Target>> offline,
                                     Component unknown) {
        if (online.size() > 1) {
            asker.sendMessage(messages.get("player-ambiguous", Placeholder.unparsed("player", name)));
            return Optional.empty();
        }
        var target = online.stream().findFirst().map(Target::of).or(offline);
        if (target.isEmpty()) {
            asker.sendMessage(unknown);
        }
        return target;
    }
}
