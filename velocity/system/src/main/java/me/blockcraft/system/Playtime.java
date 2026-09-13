package me.blockcraft.system;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.moderation.KnownPlayer;
import me.blockcraft.common.moderation.PlayerRepository;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The running session is only added to {@code bc_players} when the player leaves, so totals need it added back. */
public final class Playtime {

    private final PlayerRepository players;
    private final Logger logger;
    private final Map<UUID, Instant> sessions = new ConcurrentHashMap<>();

    public Playtime(PlayerRepository players, Logger logger) {
        this.players = players;
        this.logger = logger;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        sessions.put(event.getPlayer().getUniqueId(), Instant.now());
    }

    @Subscribe
    public EventTask onDisconnect(DisconnectEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        return EventTask.async(() -> store(player));
    }

    public Duration total(KnownPlayer player) {
        return player.playtime().plus(session(player.uuid()));
    }

    private Duration session(UUID player) {
        var start = sessions.get(player);
        return start == null ? Duration.ZERO : Duration.between(start, Instant.now());
    }

    private void store(UUID player) {
        var start = sessions.remove(player);
        if (start == null) {
            return;
        }
        try {
            players.addPlaytime(player, Duration.between(start, Instant.now()).toSeconds());
        } catch (DatabaseException e) {
            logger.warn("Could not store the playtime of {}", player, e);
        }
    }
}
