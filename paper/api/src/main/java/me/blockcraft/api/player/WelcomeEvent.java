package me.blockcraft.api.player;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired on the lobby a player new to the network arrives on first, with the rules, of which the proxy holds the one copy. */
public final class WelcomeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Component rules;

    public WelcomeEvent(Player player, Component rules) {
        super(player);
        this.rules = rules;
    }

    public Component rules() {
        return rules;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
