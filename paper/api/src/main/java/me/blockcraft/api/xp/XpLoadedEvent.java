package me.blockcraft.api.xp;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class XpLoadedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public XpLoadedEvent(Player player) {
        super(player);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
