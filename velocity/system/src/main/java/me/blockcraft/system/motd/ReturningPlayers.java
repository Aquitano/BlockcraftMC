package me.blockcraft.system.motd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.alt.AddressHasher;
import me.blockcraft.system.friends.FriendManager;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Who most likely pings the server list from an address: the account last seen logging in from it, by the keyed hash
 * the alt detection stores, when its player enabled the greeting. Clients ping again and again, so an address's
 * answer is kept for {@code server-list.cache-seconds}.
 */
public final class ReturningPlayers {

    public record Returning(String name, int friendsOnline) {
    }

    private static final int REMEMBERED_ADDRESSES = 10_000;

    private final AddressHasher hasher;
    private final PlayerRepository players;
    private final PlayerChoices choices;
    private final FriendManager friends;
    private final boolean showFriends;
    private final Logger logger;
    private final Cache<String, Optional<Returning>> recent;

    public ReturningPlayers(AddressHasher hasher, PlayerRepository players, PlayerChoices choices, FriendManager friends,
                            Duration remembered, boolean showFriends, Logger logger) {
        this.hasher = hasher;
        this.players = players;
        this.choices = choices;
        this.friends = friends;
        this.showFriends = showFriends;
        this.logger = logger;
        this.recent = CacheBuilder.newBuilder().maximumSize(REMEMBERED_ADDRESSES).expireAfterWrite(remembered).build();
    }

    /** Reads the database unless the address pinged within the cache time, so it belongs off the network thread. */
    public Optional<Returning> at(InetAddress address) {
        var hash = hasher.hash(address);
        var key = HexFormat.of().formatHex(hash);
        var known = recent.getIfPresent(key);
        if (known == null) {
            known = lookUp(hash);
            recent.put(key, known);
        }
        return known;
    }

    private Optional<Returning> lookUp(byte[] addressHash) {
        try {
            return players.lastSeenOn(addressHash)
                    .filter(player -> choices.enabled(player.uuid(), PlayerSetting.SERVER_LIST_GREETING))
                    .map(player -> new Returning(player.name(), showFriends ? friends.onlineCount(player.uuid()) : 0));
        } catch (DatabaseException e) {
            logger.warn("Could not look up who pinged the server list", e);
            return Optional.empty();
        }
    }
}
