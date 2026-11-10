package me.blockcraft.api.afk;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired on the main thread after a player went away from the keyboard or came back. */
public final class AfkChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean afk;

    public AfkChangeEvent(Player player, boolean afk) {
        super(player);
        this.afk = afk;
    }

    public boolean isAfk() {
        return afk;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
