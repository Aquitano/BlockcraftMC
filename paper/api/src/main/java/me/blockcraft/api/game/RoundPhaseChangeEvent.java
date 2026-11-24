package me.blockcraft.api.game;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RoundPhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Round round;
    private final GamePhase previous;
    private final GamePhase phase;

    public RoundPhaseChangeEvent(Round round, GamePhase previous, GamePhase phase) {
        this.round = round;
        this.previous = previous;
        this.phase = phase;
    }

    public Round round() { return round; }
    public GamePhase previous() { return previous; }
    public GamePhase phase() { return phase; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
