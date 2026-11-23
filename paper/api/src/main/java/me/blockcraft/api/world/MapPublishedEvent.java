package me.blockcraft.api.world;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread once {@code /map save} published {@link #world()} into its template. */
public final class MapPublishedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final World world;

    MapPublishedEvent(World world) {
        this.world = world;
    }

    public World world() {
        return world;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
