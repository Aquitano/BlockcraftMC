package me.blockcraft.system.friends;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyMessage.FriendsReply;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.sound.FeedbackSound;
import me.blockcraft.system.pack.NetworkPack;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.welcome.Anniversaries;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friendships across the network. A friend is an account, not a session, so the list works for players who are
 * offline; a vanished friend counts as offline and a nicked one goes by their nick, like everywhere else.
 */
public final class FriendManager {

    private record Notice(UUID player, UUID friend, LocalDate day) {
    }

    private final ProxyServer proxy;
    private final PlayerLookup lookup;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final IgnoreList ignores;
    private final ServerDirectory directory;
    private final SpectateRequests spectating;
    private final FriendRepository repository;
    private final Anniversaries anniversaries;
    private final SystemConfig config;
    private final Messages messages;
    /** Kept in memory only: a proxy restart on the day repeats a notice at most once. */
    private final Set<Notice> noticed = ConcurrentHashMap.newKeySet();
    private LocalDate lastAnniversaryDay;

    public FriendManager(ProxyServer proxy, PlayerLookup lookup, PlayerVisibility visibility, PlayerChoices choices,
                         IgnoreList ignores, ServerDirectory directory, SpectateRequests spectating, FriendRepository repository,
                         Anniversaries anniversaries, SystemConfig config, Messages messages) {
        this.proxy = proxy;
        this.lookup = lookup;
        this.visibility = visibility;
        this.choices = choices;
        this.ignores = ignores;
        this.directory = directory;
        this.spectating = spectating;
        this.repository = repository;
        this.anniversaries = anniversaries;
        this.config = config;
        this.messages = messages;
    }

    public void add(Player sender, String name) {
        findVisible(sender, name).ifPresent(target -> {
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
            if (!choices.enabled(target.uuid(), PlayerSetting.FRIEND_REQUESTS)
                    || ignores.ignores(target.uuid(), sender.getUniqueId())) {
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
        var requests = repository.requestsTo(player.getUniqueId(), config.friends().requestMinutes());
        findRelated(player, name, requests, "friends.no-request").ifPresent(requester -> {
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
        var requests = repository.requestsTo(player.getUniqueId(), config.friends().requestMinutes());
        findRelated(player, name, requests, "friends.no-request").ifPresent(requester -> {
            if (!repository.deleteRequest(requester.uuid(), player.getUniqueId())) {
                player.sendMessage(messages.get("friends.no-request", named(requester)));
                return;
            }
            player.sendMessage(messages.get("friends.denied", named(requester)));
        });
    }

    public void remove(Player player, String name) {
        findRelated(player, name, repository.friendsOf(player.getUniqueId()), "friends.not-a-friend").ifPresent(friend -> {
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
        for (var friend : friends) {
            player.sendMessage(messages.get(friend.online() ? "friends.list-online" : "friends.list-offline",
                    Placeholder.unparsed("player", friend.name()), Placeholder.unparsed("server", friend.server())));
        }
    }

    public void jump(Player player, String name) {
        findRelated(player, name, repository.friendsOf(player.getUniqueId()), "friends.not-a-friend").ifPresent(friend -> {
            var server = friend.online().flatMap(Player::getCurrentServer).map(connection -> connection.getServer());
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
            switch (directory.enter(player.getUniqueId(), target)) {
                case SEATED -> player.sendMessage(messages.get("friends.jumping", named(friend), where));
                case WATCHING -> {
                    spectating.watch(player, friend.uuid(), target);
                    player.sendMessage(messages.get("friends.watching", named(friend), where));
                }
                case CLOSED -> {
                    player.sendMessage(messages.get("friends.unavailable", named(friend)));
                    return;
                }
            }
            directory.send(player, server.get());
        });
    }

    /** What the lobby's friends menu draws: every friend once, with where they are right now. */
    public List<FriendsReply.Entry> entriesFor(Player player) {
        return repository.friendsOf(player.getUniqueId()).stream().map(friend -> {
            var online = visibleOnline(player, friend.uuid());
            String server = online.map(CommandSupport::currentServer).orElse("");
            return new FriendsReply.Entry(online.map(other -> visibility.nameFor(player, other)).orElse(friend.name()), online.isPresent(), server,
                    directory.isWatchable(server), friend.days(config.calendar().today(), config.calendar().zone()));
        }).toList();
    }

    public List<String> requestNames(Player player) {
        return repository.requestsTo(player.getUniqueId(), config.friends().requestMinutes()).stream()
                .map(friend -> nameFor(player, friend)).toList();
    }

    public List<String> friendNames(Player player) {
        return repository.friendsOf(player.getUniqueId()).stream().map(friend -> nameFor(player, friend)).toList();
    }

    public List<String> onlineFriendNames(Player player) {
        return repository.friendsOf(player.getUniqueId()).stream()
                .flatMap(friend -> visibleOnline(player, friend.uuid()).stream())
                .map(friend -> visibility.nameFor(player, friend)).toList();
    }

    public void announceJoin(Player player) {
        var today = config.calendar().today();
        for (var friend : repository.friendsOf(player.getUniqueId())) {
            announceFriendshipDay(player, friend, today);
            proxy.getPlayer(friend.uuid()).ifPresent(other -> announceFriendshipDay(other,
                    new Friend(player.getUniqueId(), player.getUsername(), friend.createdAt()), today));
        }
        var years = anniversaries.years(player.getUniqueId());
        if (years.isEmpty()) {
            announce(player, "friends.joined");
        } else if (years.getAsInt() == 1) {
            announce(player, "friends.joined-anniversary-one");
        } else {
            announce(player, "friends.joined-anniversary", Formatter.number("years", years.getAsInt()));
        }
    }

    public synchronized void announceAnniversaries() {
        var today = config.calendar().today();
        if (today.equals(lastAnniversaryDay)) {
            return;
        }
        noticed.removeIf(notice -> !notice.day().equals(today));
        for (var player : proxy.getAllPlayers()) {
            if (player.getCurrentServer().isPresent()) {
                announceFriendshipDay(player);
            }
        }
        lastAnniversaryDay = today;
    }

    private void announceFriendshipDay(Player player) {
        var today = config.calendar().today();
        for (var friend : repository.friendsOf(player.getUniqueId())) {
            announceFriendshipDay(player, friend, today);
        }
    }

    private void announceFriendshipDay(Player player, Friend friend, LocalDate today) {
        var years = friend.anniversary(today, config.calendar().zone());
        var online = proxy.getPlayer(friend.uuid());
        if (years.isEmpty() || ignores.ignores(player.getUniqueId(), friend.uuid())
                || online.filter(other -> !visibility.canSee(player, other)).isPresent()) {
            return;
        }
        if (noticed.add(new Notice(player.getUniqueId(), friend.uuid(), today))) {
            player.sendMessage(messages.get(years.getAsInt() == 1 ? "friends.anniversary-one" : "friends.anniversary",
                    Placeholder.unparsed("player", online.map(visibility::shownName).orElse(friend.name())),
                    Formatter.number("years", years.getAsInt())));
        }
    }

    public void announceQuit(Player player) {
        announce(player, "friends.left");
    }

    /** How many of the player's friends are online and not vanished; the player need not be online themselves. */
    public int onlineCount(UUID player) {
        return (int) repository.friendUuidsOf(player).stream()
                .flatMap(friend -> proxy.getPlayer(friend).stream())
                .filter(friend -> !visibility.isVanished(friend))
                .count();
    }

    public void purgeExpiredRequests() {
        repository.deleteExpiredRequests(config.friends().requestMinutes());
    }

    private void announce(Player player, String key, TagResolver... details) {
        var line = TagResolver.resolver(Placeholder.unparsed("player", shownName(player)), TagResolver.resolver(details));
        for (UUID friendId : repository.friendUuidsOf(player.getUniqueId())) {
            proxy.getPlayer(friendId)
                    .filter(friend -> visibility.canSee(friend, player))
                    .ifPresent(friend -> friend.sendMessage(messages.get(key, line)));
        }
    }

    private void invite(Player invitee, Player requester) {
        var name = Placeholder.unparsed("player", shownName(requester));
        invitee.sendMessage(messages.get("friends.request-received", name));
        if (choices.enabled(invitee, PlayerSetting.SOUNDS)) {
            invitee.playSound(FeedbackSound.NOTIFY.sound(NetworkPack.applied(invitee)));
        }
        invitee.sendMessage(messages.get("friends.request-accept", name)
                .clickEvent(ClickEvent.runCommand("/friend accept " + shownName(requester)))
                .hoverEvent(HoverEvent.showText(messages.get("friends.request-accept-hover", name))));
    }

    /** Tells the sender when nobody goes by that name, so every caller can act on a hit alone. */
    private Optional<PlayerLookup.Target> findVisible(Player sender, String name) {
        var target = lookup.findVisible(sender, name).map(found -> new PlayerLookup.Target(found.uuid(),
                found.online().map(online -> visibility.nameFor(sender, online)).orElse(found.name()), found.online()));
        if (target.isEmpty()) {
            sender.sendMessage(messages.get("friends.unknown", Placeholder.unparsed("player", name)));
        }
        return target;
    }

    private Optional<PlayerLookup.Target> findRelated(Player player, String name, List<Friend> related, String missing) {
        // An account name already on the list stays usable without revealing the nick or presence behind it.
        var account = related.stream().filter(friend -> friend.name().equalsIgnoreCase(name)).findFirst()
                .map(friend -> new PlayerLookup.Target(friend.uuid(), friend.name(), visibleOnline(player, friend.uuid())
                        .filter(online -> visibility.nick(online).isEmpty() || visibility.seesRealNames(player))));
        var target = account.or(() -> visibility.find(player, name)
                .filter(online -> related.stream().anyMatch(friend -> friend.uuid().equals(online.getUniqueId())))
                .map(online -> new PlayerLookup.Target(online.getUniqueId(), visibility.nameFor(player, online), Optional.of(online))));
        if (target.isEmpty()) {
            player.sendMessage(messages.get(missing, Placeholder.unparsed("player", name)));
        }
        return target;
    }

    private Optional<Player> visibleOnline(Player viewer, UUID friend) {
        return proxy.getPlayer(friend).filter(online -> visibility.canSee(viewer, online));
    }

    private String nameFor(Player viewer, Friend friend) {
        return visibleOnline(viewer, friend.uuid()).map(online -> visibility.nameFor(viewer, online)).orElse(friend.name());
    }

    private TagResolver named(PlayerLookup.Target target) {
        return Placeholder.unparsed("player", target.name());
    }

    private String shownName(Player player) {
        return visibility.shownName(player);
    }
}
