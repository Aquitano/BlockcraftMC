package me.blockcraft.api.settings;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the main thread once a player's stored settings are in the cache, a few ticks after they joined.
 * Anything that has to re-apply a remembered choice (the lobby's player hider) waits for this instead of
 * reading the cache while it is still empty.
 */
public final class PlayerSettingsLoadedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerSettingsLoadedEvent(Player player) {
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
