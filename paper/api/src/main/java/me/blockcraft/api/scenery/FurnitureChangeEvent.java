package me.blockcraft.api.scenery;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Optional;

public final class FurnitureChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ItemDisplay entity;
    private final Optional<Boolean> door;
    private boolean applied;

    public FurnitureChangeEvent(ItemDisplay entity) { this(entity, Optional.empty()); }
    public FurnitureChangeEvent(ItemDisplay entity, boolean open) { this(entity, Optional.of(open)); }
    private FurnitureChangeEvent(ItemDisplay entity, Optional<Boolean> door) {
        this.entity = entity;
        this.door = door;
    }

    public ItemDisplay entity() { return entity; }
    public Optional<Boolean> door() { return door; }
    public boolean applied() { return applied; }
    public void applied(boolean value) { applied = value; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
