package me.blockcraft.system.friends;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.network.ServerDirectory;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Friendships across the network. A friend is an account, not a session, so the list works for players who are
 * offline; a vanished friend counts as offline and a nicked one goes by their nick, like everywhere else.
 */
public final class FriendManager {

    /** One line of the friend list, as both {@code /friend list} and the lobby's menu show it. */
    public record Entry(String name, boolean online, String server, boolean watching) {
    }

    private final ProxyServer proxy;
    private final PlayerLookup lookup;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final ServerDirectory directory;
    private final SpectateRequests spectating;
    private final FriendRepository repository;
    private final SystemConfig config;
    private final Messages messages;

    public FriendManager(ProxyServer proxy, PlayerLookup lookup, PlayerVisibility visibility, PlayerChoices choices,
                         ServerDirectory directory, SpectateRequests spectating, FriendRepository repository,
                         SystemConfig config, Messages messages) {
        this.proxy = proxy;
        this.lookup = lookup;
        this.visibility = visibility;
        this.choices = choices;
        this.directory = directory;
        this.spectating = spectating;
        this.repository = repository;
        this.config = config;
        this.messages = messages;
    }

    public void add(Player sender, String name) {
        find(sender, name).ifPresent(target -> {
            if (target.uuid().equals(sender.getUniqueId())) {
                sender.sendMessage(messages.get("friends.self"));
                return;
            }
            if (repository.areFriends(sender.getUniqueId(), target.uuid())) {
                sender.sendMessage(messages.get("friends.already", named(target)));
                return;
            }
            int max = config.friends().max();
            if (repository.countFriends(sender.getUniqueId()) >= max) {
                sender.sendMessage(messages.get("friends.full", Formatter.number("max", max)));
                return;
            }
            if (repository.countFriends(target.uuid()) >= max) {
                sender.sendMessage(messages.get("friends.target-full", named(target)));
                return;
            }
            if (!choices.enabled(target.uuid(), PlayerSetting.FRIEND_REQUESTS)) {
                sender.sendMessage(messages.get("friends.requests-disabled", named(target)));
                return;
            }
            if (repository.hasOpenRequest(sender.getUniqueId(), target.uuid(), config.friends().requestMinutes())) {
                sender.sendMessage(messages.get("friends.already-requested", named(target)));
                return;
            }
            repository.request(sender.getUniqueId(), target.uuid());
            sender.sendMessage(messages.get("friends.requested", named(target),
                    Formatter.number("minutes", config.friends().requestMinutes())));
            target.online().ifPresent(online -> invite(online, sender));
        });
    }

    public void accept(Player player, String name) {
        find(player, name).ifPresent(requester -> {
            int max = config.friends().max();
            if (repository.countFriends(player.getUniqueId()) >= max) {
                player.sendMessage(messages.get("friends.full", Formatter.number("max", max)));
                return;
            }
            if (repository.countFriends(requester.uuid()) >= max) {
                player.sendMessage(messages.get("friends.target-full", named(requester)));
                return;
            }
            if (!repository.accept(requester.uuid(), player.getUniqueId(), config.friends().requestMinutes())) {
                player.sendMessage(messages.get("friends.no-request", named(requester)));
                return;
            }
            player.sendMessage(messages.get("friends.accepted", named(requester)));
            requester.online().ifPresent(online ->
                    online.sendMessage(messages.get("friends.accepted-by", Placeholder.unparsed("player", shownName(player)))));
        });
    }

    public void deny(Player player, String name) {
        find(player, name).ifPresent(requester -> {
            if (!repository.deleteRequest(requester.uuid(), player.getUniqueId())) {
                player.sendMessage(messages.get("friends.no-request", named(requester)));
                return;
            }
            player.sendMessage(messages.get("friends.denied", named(requester)));
        });
    }

    public void remove(Player player, String name) {
        find(player, name).ifPresent(friend -> {
            if (!repository.remove(player.getUniqueId(), friend.uuid())) {
                player.sendMessage(messages.get("friends.not-a-friend", named(friend)));
                return;
            }
            player.sendMessage(messages.get("friends.removed", named(friend)));
            friend.online().ifPresent(online ->
                    online.sendMessage(messages.get("friends.removed-by", Placeholder.unparsed("player", shownName(player)))));
        });
    }

    public void list(Player player) {
        var requests = requestNames(player);
        requests.forEach(name -> player.sendMessage(messages.get("friends.list-request", Placeholder.unparsed("player", name))
                .clickEvent(ClickEvent.suggestCommand("/friend accept " + name))
                .hoverEvent(HoverEvent.showText(messages.get("friends.list-request-hover", Placeholder.unparsed("player", name))))));
        var friends = entriesFor(player);
        if (friends.isEmpty()) {
            player.sendMessage(messages.get("friends.list-empty"));
            return;
        }
        player.sendMessage(messages.get("friends.list-header", Formatter.number("count", friends.size()),
                Formatter.number("max", config.friends().max())));
        for (Entry friend : friends) {
            player.sendMessage(messages.get(friend.online() ? "friends.list-online" : "friends.list-offline",
                    Placeholder.unparsed("player", friend.name()), Placeholder.unparsed("server", friend.server())));
        }
    }

    public void jump(Player player, String name) {
        find(player, name).ifPresent(friend -> {
            if (!repository.areFriends(player.getUniqueId(), friend.uuid())) {
                player.sendMessage(messages.get("friends.not-a-friend", named(friend)));
                return;
            }
            var online = friend.online().filter(other -> visibility.canSee(player, other));
            var server = online.flatMap(Player::getCurrentServer).map(connection -> connection.getServer());
            if (server.isEmpty()) {
                player.sendMessage(messages.get("friends.offline", named(friend)));
                return;
            }
            if (player.getCurrentServer().map(current -> current.getServer().equals(server.get())).orElse(false)) {
                player.sendMessage(messages.get("friends.already-there", named(friend)));
                return;
            }
            String target = server.get().getServerInfo().getName();
            var where = Placeholder.unparsed("server", target);
            if (directory.isJoinable(target)) {
                player.sendMessage(messages.get("friends.jumping", named(friend), where));
            } else if (directory.isWatchable(target)) {
                spectating.watch(player, friend.uuid(), target);
                player.sendMessage(messages.get("friends.watching", named(friend), where));
            } else {
                player.sendMessage(messages.get("friends.unavailable", named(friend)));
                return;
            }
            player.createConnectionRequest(server.get()).fireAndForget();
        });
    }

    /** What the lobby's friends menu draws: every friend once, with where they are right now. */
    public List<Entry> entriesFor(Player player) {
        return repository.friendsOf(player.getUniqueId()).stream().map(friend -> {
            var online = proxy.getPlayer(friend.uuid()).filter(other -> visibility.canSee(player, other));
            String server = online.map(CommandSupport::currentServer).orElse("");
            return new Entry(online.map(visibility::shownName).orElse(friend.name()), online.isPresent(), server,
                    directory.isWatchable(server));
        }).toList();
    }

    public List<String> requestNames(Player player) {
        return repository.requestsTo(player.getUniqueId(), config.friends().requestMinutes()).stream()
                .map(Friend::name).toList();
    }

    public void announceJoin(Player player) {
        announce(player, "friends.joined");
    }

    public void announceQuit(Player player) {
        announce(player, "friends.left");
    }

    public void purgeExpiredRequests() {
        repository.deleteExpiredRequests(config.friends().requestMinutes());
    }

    private void announce(Player player, String key) {
        var line = Placeholder.unparsed("player", shownName(player));
        for (UUID friendId : repository.friendUuidsOf(player.getUniqueId())) {
            proxy.getPlayer(friendId)
                    .filter(friend -> visibility.canSee(friend, player))
                    .ifPresent(friend -> friend.sendMessage(messages.get(key, line)));
        }
    }

    private void invite(Player invitee, Player requester) {
        var name = Placeholder.unparsed("player", shownName(requester));
        invitee.sendMessage(messages.get("friends.request-received", name));
        invitee.sendMessage(messages.get("friends.request-accept", name)
                .clickEvent(ClickEvent.runCommand("/friend accept " + shownName(requester)))
                .hoverEvent(HoverEvent.showText(messages.get("friends.request-accept-hover", name))));
    }

    /** Tells the sender when nobody goes by that name, so every caller can act on a hit alone. */
    private Optional<PlayerLookup.Target> find(Player sender, String name) {
        var target = lookup.find(name);
        if (target.isEmpty()) {
            sender.sendMessage(messages.get("friends.unknown", Placeholder.unparsed("player", name)));
        }
        return target;
    }

    private TagResolver named(PlayerLookup.Target target) {
        return Placeholder.unparsed("player", target.online().map(visibility::shownName).orElse(target.name()));
    }

    private String shownName(Player player) {
        return visibility.shownName(player);
    }
}
