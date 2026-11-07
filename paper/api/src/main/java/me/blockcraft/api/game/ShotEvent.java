package me.blockcraft.api.game;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when a fighter fires a gun that leaves no projectile and swings nothing, so whoever shows the
 * round again, such as the replay recorder, hears of it here.
 */
public final class ShotEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity shooter;

    public ShotEvent(LivingEntity shooter) {
        this.shooter = shooter;
    }

    public LivingEntity shooter() {
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
