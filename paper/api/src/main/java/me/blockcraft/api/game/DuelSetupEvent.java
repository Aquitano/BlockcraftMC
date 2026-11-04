package me.blockcraft.api.game;

import me.blockcraft.common.network.ProxyMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

public final class DuelSetupEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ProxyMessage.DuelSetup setup;

    public DuelSetupEvent(Player player, ProxyMessage.DuelSetup setup) {
        super(player);
        this.setup = setup;
    }

    public ProxyMessage.DuelSetup setup() { return setup; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
