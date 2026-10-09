package me.blockcraft.system.ignore;

import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.ignore.IgnoreRepository;
import me.blockcraft.common.ignore.IgnoreRepository.Ignored;
import me.blockcraft.common.network.ProxyChannel;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Whom each player ignores, by account, so a nick changes nothing. The list of an online player is read once at
 * login; every change is written here and told to the server the player is on, whose chat leaves the ignored
 * player out.
 */
public final class IgnoreList {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyChannel.NAME);

    private final IgnoreRepository repository;
    private final Logger logger;
    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();

    public IgnoreList(IgnoreRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        return EventTask.async(() -> {
            try {
                ignored.put(player, repository.list(player).stream().map(Ignored::uuid)
                        .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
            } catch (DatabaseException e) {
                logger.warn("Could not read the ignore list of {}", player, e);
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ignored.remove(event.getPlayer().getUniqueId());
    }

    /** Both players are online, so the list read at login answers. */
    public boolean ignores(Player player, Player other) {
        return ignored.getOrDefault(player.getUniqueId(), Set.of()).contains(other.getUniqueId());
    }

    /** Reads the table for a player who is not online, so a friend request to an absent player is judged too. */
    public boolean ignores(UUID player, UUID other) {
        var cached = ignored.get(player);
        return cached == null ? repository.ignores(player, other) : cached.contains(other);
    }

    public List<Ignored> list(Player player) {
        return repository.list(player.getUniqueId());
    }

    /** False when the account was already ignored. */
    public boolean add(Player player, Ignored other) {
        if (!repository.add(player.getUniqueId(), other)) {
            return false;
        }
        ignored.computeIfPresent(player.getUniqueId(), (ignoredBy, accounts) -> {
            accounts.add(other.uuid());
            return accounts;
        });
        tell(player, other.uuid(), true);
        return true;
    }

    /** False when the account was not ignored. */
    public boolean remove(Player player, UUID other) {
        if (!repository.remove(player.getUniqueId(), other)) {
            return false;
        }
        ignored.computeIfPresent(player.getUniqueId(), (ignoredBy, accounts) -> {
            accounts.remove(other);
            return accounts;
        });
        tell(player, other, false);
        return true;
    }

    private static void tell(Player player, UUID other, boolean ignoring) {
        var out = ByteStreams.newDataOutput();
        out.writeUTF(ProxyChannel.IGNORE);
        out.writeUTF(other.toString());
        out.writeBoolean(ignoring);
        player.getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(CHANNEL, out.toByteArray()));
    }
}
