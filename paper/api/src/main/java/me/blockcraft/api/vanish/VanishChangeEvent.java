package me.blockcraft.api.vanish;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired after a player vanished or reappeared on this server, so the tab list can mark them for the staff who still see them. */
public final class VanishChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean vanished;

    public VanishChangeEvent(Player player, boolean vanished) {
        super(player);
        this.vanished = vanished;
    }

    public boolean isVanished() {
        return vanished;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
