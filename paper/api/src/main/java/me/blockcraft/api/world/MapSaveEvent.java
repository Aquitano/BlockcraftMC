package me.blockcraft.api.world;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread right before {@code /map save} flushes the world into the template: put back every block
 * that only stands there for a moment, or the template keeps it.
 */
public final class MapSaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
