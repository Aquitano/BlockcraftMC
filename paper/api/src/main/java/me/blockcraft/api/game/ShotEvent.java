package me.blockcraft.api.game;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when a player fires a gun that hits instantly: such a shot leaves no projectile and makes
 * the shooter swing nothing, so whoever shows the round again, such as the replay recorder, hears of it here.
 */
public final class ShotEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player shooter;

    public ShotEvent(Player shooter) {
        this.shooter = shooter;
    }

    public Player shooter() {
        return shooter;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
