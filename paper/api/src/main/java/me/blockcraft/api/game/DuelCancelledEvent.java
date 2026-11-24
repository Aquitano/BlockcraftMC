package me.blockcraft.api.game;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import java.util.UUID;

public final class DuelCancelledEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID ticket;

    public DuelCancelledEvent(Player player, UUID ticket) {
        super(player);
        this.ticket = ticket;
    }

    public UUID ticket() { return ticket; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
