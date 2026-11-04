package me.blockcraft.api.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired on the main thread right before {@code /map save} flushes {@link #world()} into its template: put back every
 * block in that world that only stands there for a moment, or the template keeps it.
 */
public final class MapSaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final World world;
    private final List<Runnable> restorations = new ArrayList<>();
    private RuntimeException failure;

    MapSaveEvent(World world) {
        this.world = world;
    }

    /**
     * Fires the event, publishes unless a listener aborted, and runs every restoration either way. A failed
     * restoration does not undo a publish, so it comes back as a value; a failed publish carries them as suppressed.
     */
    static List<RuntimeException> publish(World world, Runnable publish) {
        var event = new MapSaveEvent(world);
        try {
            Bukkit.getPluginManager().callEvent(event);
            event.checkReady();
            publish.run();
        } catch (RuntimeException failure) {
            event.complete().forEach(failure::addSuppressed);
            throw failure;
        }
        return event.complete();
    }

    public World world() {
        return world;
    }

    public void abort(RuntimeException failure) {
        if (this.failure == null) {
            this.failure = failure;
        } else {
            this.failure.addSuppressed(failure);
        }
    }

    void checkReady() {
        if (failure != null) {
            throw failure;
        }
    }

    /** Clears temporary state for the save, aborting it if that fails, and restores the state afterwards either way. */
    public void suspend(Runnable clear, Runnable restore) {
        afterSave(restore);
        try {
            clear.run();
        } catch (RuntimeException failure) {
            abort(failure);
        }
    }

    /** Restores temporary state after the save attempt, including when flushing or publishing fails. */
    public void afterSave(Runnable restore) {
        restorations.add(restore);
    }

    List<RuntimeException> complete() {
        var failures = new ArrayList<RuntimeException>();
        for (var restoration : restorations) {
            try {
                restoration.run();
            } catch (RuntimeException failure) {
                failures.add(failure);
            }
        }
        return failures;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
