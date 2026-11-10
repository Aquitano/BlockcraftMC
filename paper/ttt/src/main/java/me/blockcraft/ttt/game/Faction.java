package me.blockcraft.ttt.game;

import java.util.Collection;
import java.util.Optional;

/** The two sides a round is won by: the traitors, or everyone else. */
public enum Faction {
    TRAITORS,
    INNOCENTS;

    /** The side the living decide for: no traitor left, or nobody but traitors. While both are alive, nobody. */
    public static Optional<Faction> winner(Collection<Role> alive) {
        if (alive.stream().noneMatch(role -> role.faction() == TRAITORS)) {
            return Optional.of(INNOCENTS);
        }
        if (alive.stream().allMatch(role -> role.faction() == TRAITORS)) {
            return Optional.of(TRAITORS);
        }
        return Optional.empty();
    }

    public String key() {
        return name().toLowerCase();
    }
}
