package me.blockcraft.api.world;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired on the main thread right before {@code /map save} flushes the world into the template: put back every block
 * that only stands there for a moment, or the template keeps it.
 */
public final class MapSaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final List<Runnable> restorations = new ArrayList<>();

    /** Restores temporary state after the save attempt, including when flushing or publishing fails. */
    public void afterSave(Runnable restore) {
        restorations.add(restore);
    }

    void complete() {
        restorations.forEach(Runnable::run);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
