package me.blockcraft.sternenfront.game;

import java.util.Optional;

final class PostThreat {

    private Optional<Faction> previous = Optional.empty();
    private Optional<Faction> pending = Optional.empty();
    private long readyAt;

    Optional<Faction> update(Optional<Faction> threatened, long tick, int cooldownTicks) {
        boolean entering = threatened.isPresent() && !threatened.equals(previous);
        previous = threatened;
        if (threatened.isEmpty() || entering) {
            pending = threatened;
        }
        if (pending.isPresent() && tick >= readyAt) {
            readyAt = tick + cooldownTicks;
            var alert = pending;
            pending = Optional.empty();
            return alert;
        }
        return Optional.empty();
    }
}
