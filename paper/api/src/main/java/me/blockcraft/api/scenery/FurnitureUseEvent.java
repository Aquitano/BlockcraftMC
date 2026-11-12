package me.blockcraft.api.scenery;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class FurnitureUseEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ItemDisplay entity;
    private boolean cancelled;

    public FurnitureUseEvent(Player player, ItemDisplay entity) {
        this.player = player;
        this.entity = entity;
    }

    public Player player() { return player; }
    public ItemDisplay entity() { return entity; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
