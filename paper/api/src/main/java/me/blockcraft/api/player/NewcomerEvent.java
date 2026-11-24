package me.blockcraft.api.player;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.time.Duration;
import java.util.UUID;

/**
 * Fired when the proxy says that someone joined the network for the first time; for {@link #window()} from now, a
 * greeting written on this server counts for them. The newcomer is usually on another server.
 */
public final class NewcomerEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID newcomer;
    private final String name;
    private final Duration window;

    public NewcomerEvent(UUID newcomer, String name, Duration window) {
        this.newcomer = newcomer;
        this.name = name;
        this.window = window;
    }

    public UUID newcomer() {
        return newcomer;
    }

    public String name() {
        return name;
    }

    public Duration window() {
        return window;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
