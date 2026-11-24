package me.blockcraft.sternenfront.game;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A bolt struck something in the map that is not a player: an {@link Interaction} tagged with {@link #MARK}, such as
 * a droid that runs from fire. The bolt stops there; what the target does is up to whoever listens.
 */
public final class TargetShotEvent extends Event {

    public static final NamespacedKey MARK = new NamespacedKey("sternenfront", "target");

    private static final HandlerList HANDLERS = new HandlerList();
    private final Interaction target;
    private final Player shooter;

    public TargetShotEvent(Interaction target, Player shooter) {
        this.target = target;
        this.shooter = shooter;
    }

    public Interaction target() {
        return target;
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
