package me.blockcraft.api.ranked;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.UUID;

/** The proxy's ranked queue paired the player with {@code opponent}, and both are on this server now. */
public final class RankedMatchEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID opponent;

    public RankedMatchEvent(Player player, UUID opponent) {
        super(player);
        this.opponent = opponent;
    }

    public UUID opponent() {
        return opponent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
