package me.blockcraft.system.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.FreezeRepository;
import org.slf4j.Logger;

/**
 * Keeps a frozen player on their server. The proxy's own switches (/hub, /play, JoinMe, party and friend jumps) never
 * pass the backend's command filter, so they are refused here. A player without a server yet, such as a login or one
 * redirected after a kick, is let through.
 */
public final class FreezeSwitchListener {

    private final FreezeRepository frozen;
    private final Messages messages;
    private final Logger logger;

    public FreezeSwitchListener(FreezeRepository frozen, Messages messages, Logger logger) {
        this.frozen = frozen;
        this.messages = messages;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPreConnect(ServerPreConnectEvent event) {
        var player = event.getPlayer();
        if (player.getCurrentServer().isEmpty()) {
            return null;
        }
        return EventTask.async(() -> {
            try {
                if (frozen.issuer(player.getUniqueId()).isPresent()) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    player.sendMessage(messages.get("freeze.switch-denied"));
                }
            } catch (DatabaseException e) {
                logger.warn("Could not check whether {} is frozen before a server switch", player.getUsername(), e);
            }
        });
    }
}
