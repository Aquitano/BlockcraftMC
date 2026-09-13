package me.blockcraft.api.nick;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired after a player's profile was swapped for a nick or back. Their {@link Player#getName()} has changed, so
 * anything keyed by name (tab teams, hider lists) must re-register them.
 */
public final class NickChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public NickChangeEvent(Player player) {
        super(player);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
