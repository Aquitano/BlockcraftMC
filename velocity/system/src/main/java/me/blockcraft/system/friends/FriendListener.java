package me.blockcraft.system.friends;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.PlayerVisibility;
import org.slf4j.Logger;

/** Tells a player's friends when they arrive on the network and when they leave it. */
public final class FriendListener {

    private static final short BEFORE_VISIBILITY = 1;

    private final FriendManager friends;
    private final PlayerVisibility visibility;
    private final Logger logger;

    public FriendListener(FriendManager friends, PlayerVisibility visibility, Logger logger) {
        this.friends = friends;
        this.visibility = visibility;
        this.logger = logger;
    }

    /** The first server connect, not the login: a player who is still connecting has nothing to be greeted for. */
    @Subscribe
    public EventTask onServerPostConnect(ServerPostConnectEvent event) {
        if (event.getPreviousServer() != null) {
            return null;
        }
        return EventTask.async(() -> announce(() -> friends.announceJoin(event.getPlayer())));
    }

    /** Runs before PlayerVisibility forgets the player, so a vanished staff member leaves as quietly as they came. */
    @Subscribe(priority = BEFORE_VISIBILITY)
    public EventTask onDisconnect(DisconnectEvent event) {
        if (!LiveSession.endedBy(event) || visibility.isVanished(event.getPlayer())) {
            return null;
        }
        return EventTask.async(() -> announce(() -> friends.announceQuit(event.getPlayer())));
    }

    private void announce(Runnable announcement) {
        try {
            announcement.run();
        } catch (DatabaseException e) {
            logger.warn("Could not tell a player's friends that they came or went", e);
        }
    }
}
