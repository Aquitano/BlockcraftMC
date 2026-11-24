package me.blockcraft.api.spectate;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

/**
 * Fired when the proxy says that this player came to watch a friend, which arrives after they joined and after the
 * game turned them into a spectator. Only the game knows whether that happened, so it decides what to do with it.
 */
public final class SpectateRequestEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID target;

    public SpectateRequestEvent(Player player, UUID target) {
        super(player);
        this.target = target;
    }

    /** The account of the friend, which is on this server unless they left between the jump and the arrival. */
    public UUID target() {
        return target;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
