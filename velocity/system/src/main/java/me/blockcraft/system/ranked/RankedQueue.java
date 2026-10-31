package me.blockcraft.system.ranked;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.elo.EloRepository;
import me.blockcraft.common.elo.Season;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.network.OnDemandServers;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ranked Duell queue. Players wait in a lobby, two whose Elo is close enough ({@link Matchmaking}) meet on an empty
 * server of the Duell group, and once both are there the proxy tells that server to play their series rated. A
 * sleeping group is woken for them while they keep waiting. Parties cannot queue, and leaving the lobby leaves the queue.
 */
public final class RankedQueue {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);
    private static final int SECONDS_PER_MINUTE = 60;

    public record Settings(String group, int baseRange, int rangePerSecond, int maxRange, Duration matchTimeout) {
    }

    private record Match(UUID first, UUID second, Instant since) {

        UUID opponentOf(UUID player) {
            return player.equals(first) ? second : first;
        }
    }

    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final LobbyConnector lobby;
    private final PartyManager parties;
    private final Optional<OnDemandServers> onDemand;
    private final EloRepository elo;
    private final Settings settings;
    private final Messages messages;
    private final Logger logger;
    private final Clock clock;
    private final Map<UUID, Matchmaking.Seeker> seekers = new LinkedHashMap<>();
    private final Map<UUID, Match> matches = new HashMap<>();

    public RankedQueue(ProxyServer proxy, ServerDirectory directory, LobbyConnector lobby, PartyManager parties,
                       Optional<OnDemandServers> onDemand, EloRepository elo, Settings settings, Messages messages,
                       Logger logger, Clock clock) {
        this.proxy = proxy;
        this.directory = directory;
        this.lobby = lobby;
        this.parties = parties;
        this.onDemand = onDemand;
        this.elo = elo;
        this.settings = settings;
        this.messages = messages;
        this.logger = logger;
        this.clock = clock;
    }

    /** Blocks on the database for the player's rating this season. */
    public void join(Player player) {
        var id = player.getUniqueId();
        if (!lobby.isOnLobby(player)) {
            player.sendMessage(messages.get("ranked.lobby-only"));
            return;
        }
        if (parties.isInParty(id)) {
            player.sendMessage(messages.get("ranked.party"));
            return;
        }
        if (waiting(id)) {
            player.sendMessage(messages.get("ranked.already"));
            return;
        }
        var season = Season.current();
        int rating;
        try {
            rating = elo.get(id, season).rating();
        } catch (DatabaseException e) {
            logger.warn("Could not load the Elo of {} for the ranked queue", player.getUsername(), e);
            player.sendMessage(messages.get("ranked.failed"));
            return;
        }
        synchronized (this) {
            seekers.putIfAbsent(id, new Matchmaking.Seeker(id, rating, clock.instant()));
        }
        player.sendMessage(messages.get("ranked.joined", Formatter.number("rating", rating),
                Placeholder.unparsed("season", season.key())));
    }

    public void leave(Player player) {
        boolean left;
        synchronized (this) {
            left = seekers.remove(player.getUniqueId()) != null;
        }
        player.sendMessage(messages.get(left ? "ranked.left" : "ranked.not-queued"));
    }

    private synchronized boolean waiting(UUID player) {
        return seekers.containsKey(player) || matches.containsKey(player);
    }

    /** Every second: drops matches that never came together, shows the waiting their search and pairs whoever fits. */
    public void tick() {
        var now = clock.instant();
        var expired = new HashSet<UUID>();
        List<Matchmaking.Pair> pairs;
        synchronized (this) {
            matches.entrySet().removeIf(entry -> {
                boolean stale = Duration.between(entry.getValue().since(), now).compareTo(settings.matchTimeout()) > 0;
                if (stale) {
                    expired.add(entry.getKey());
                }
                return stale;
            });
            for (var seeker : seekers.values()) {
                long waited = Duration.between(seeker.since(), now).toSeconds();
                proxy.getPlayer(seeker.player()).ifPresent(player -> player.sendActionBar(messages.get("ranked.searching",
                        Placeholder.unparsed("time", "%d:%02d".formatted(waited / SECONDS_PER_MINUTE, waited % SECONDS_PER_MINUTE)),
                        Formatter.number("range", Matchmaking.range(seeker, now, settings)))));
            }
            pairs = Matchmaking.pairs(seekers.values(), now, settings);
        }
        expired.forEach(this::cancelled);
        pairs.forEach(pair -> start(pair, now));
    }

    /**
     * A pair meets on an empty server of the group, both seated at once. Without one both keep waiting, and a sleeping
     * group is woken so that a later tick finds its server; nobody is queued on it, which could split the pair.
     */
    private void start(Matchmaking.Pair pair, Instant now) {
        var first = proxy.getPlayer(pair.first().player());
        var second = proxy.getPlayer(pair.second().player());
        if (first.isEmpty() || second.isEmpty()) {
            return;
        }
        for (var player : List.of(first.get(), second.get())) {
            if (parties.isInParty(player.getUniqueId())) {
                synchronized (this) {
                    seekers.remove(player.getUniqueId());
                }
                player.sendMessage(messages.get("ranked.party"));
                return;
            }
        }
        var ids = List.of(pair.first().player(), pair.second().player());
        var server = directory.fill(settings.group(), ids);
        if (server.isEmpty()) {
            onDemand.ifPresent(servers -> servers.wake(settings.group()));
            return;
        }
        var target = server.get();
        var match = new Match(pair.first().player(), pair.second().player(), now);
        synchronized (this) {
            if (!seekers.containsKey(match.first()) || !seekers.containsKey(match.second())) {
                ids.forEach(id -> directory.release(id, target.getServerInfo().getName()));
                return;
            }
            seekers.remove(match.first());
            seekers.remove(match.second());
            matches.put(match.first(), match);
            matches.put(match.second(), match);
        }
        found(first.get(), pair.second());
        found(second.get(), pair.first());
        for (var player : List.of(first.get(), second.get())) {
            directory.send(player, target).thenAccept(arrived -> {
                if (!arrived) {
                    abandon(match);
                }
            });
        }
    }

    /** Both hear that the match is off; whoever already stands on its server plays there unrated. */
    private void abandon(Match match) {
        boolean dropped;
        synchronized (this) {
            dropped = matches.remove(match.first(), match) | matches.remove(match.second(), match);
        }
        if (dropped) {
            List.of(match.first(), match.second()).forEach(this::cancelled);
        }
    }

    private void found(Player player, Matchmaking.Seeker opponent) {
        var name = proxy.getPlayer(opponent.player()).map(Player::getUsername).orElse("?");
        player.sendMessage(messages.get("ranked.found", Placeholder.unparsed("opponent", name),
                Formatter.number("rating", opponent.rating())));
    }

    private void cancelled(UUID player) {
        proxy.getPlayer(player).ifPresent(online -> online.sendMessage(messages.get("ranked.cancelled")));
    }

    /**
     * Leaving the lobby for anything but the match leaves the queue. Once both of a pair stand on the same server of
     * the group, that server learns their series is rated.
     */
    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        var server = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
        boolean inGroup = directory.groupOf(server).filter(settings.group()::equals).isPresent();
        Optional<Match> together = Optional.empty();
        Optional<Match> abandoned = Optional.empty();
        boolean leftQueue = false;
        synchronized (this) {
            if (!directory.isLobby(server) && seekers.remove(id) != null) {
                leftQueue = true;
            }
            var match = matches.get(id);
            if (match != null && inGroup) {
                var opponent = proxy.getPlayer(match.opponentOf(id));
                boolean arrived = opponent.flatMap(Player::getCurrentServer)
                        .filter(connection -> connection.getServerInfo().getName().equals(server)).isPresent();
                if (arrived) {
                    matches.remove(match.first());
                    matches.remove(match.second());
                    together = Optional.of(match);
                }
            } else if (match != null && !directory.isLobby(server)) {
                abandoned = Optional.of(match);
            }
        }
        if (leftQueue) {
            player.sendMessage(messages.get("ranked.left"));
        }
        abandoned.ifPresent(this::abandon);
        together.ifPresent(this::rate);
    }

    private void rate(Match match) {
        for (var id : List.of(match.first(), match.second())) {
            var payload = ProxyMessageCodec.encode(new ProxyMessage.RankedMatch(match.opponentOf(id)));
            proxy.getPlayer(id).flatMap(Player::getCurrentServer)
                    .ifPresent(connection -> connection.sendPluginMessage(CHANNEL, payload));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!LiveSession.endedBy(event)) {
            return;
        }
        var id = event.getPlayer().getUniqueId();
        Optional<UUID> opponent;
        synchronized (this) {
            seekers.remove(id);
            var match = Optional.ofNullable(matches.remove(id));
            opponent = match.map(left -> left.opponentOf(id));
            opponent.ifPresent(matches::remove);
        }
        opponent.ifPresent(this::cancelled);
    }
}
