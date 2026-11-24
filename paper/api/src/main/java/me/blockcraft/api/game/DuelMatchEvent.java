package me.blockcraft.api.game;

import me.blockcraft.common.network.ProxyMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class DuelMatchEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ProxyMessage.DuelMatch match;

    public DuelMatchEvent(Player player, ProxyMessage.DuelMatch match) {
        super(player);
        this.match = match;
    }

    public ProxyMessage.DuelMatch match() { return match; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
