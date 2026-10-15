package me.blockcraft.api.nick;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import java.util.Optional;

/**
 * Fired after a player's profile was swapped for a nick or back. Their {@link Player#getName()} has changed, so
 * anything keyed by name (tab teams, hider lists) must re-register them, and while the nick is on they show the
 * rank of {@link #rankGroup()} instead of their own.
 */
public final class NickChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Optional<String> rankGroup;

    /** {@code rankGroup} is the LuckPerms group the nick wears, empty when the nick came off. */
    public NickChangeEvent(Player player, Optional<String> rankGroup) {
        super(player);
        this.rankGroup = rankGroup;
    }

    public Optional<String> rankGroup() {
        return rankGroup;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
