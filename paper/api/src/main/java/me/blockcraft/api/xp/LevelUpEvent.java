package me.blockcraft.api.xp;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired on the main thread once an award lifted the player's stored total onto a new level. */
public final class LevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int level;

    public LevelUpEvent(Player player, int level) {
        super(player);
        this.level = level;
    }

    public int level() {
        return level;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
