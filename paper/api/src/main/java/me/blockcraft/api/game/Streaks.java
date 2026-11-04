package me.blockcraft.api.game;

import java.util.List;

/** The kill streak lengths a round announces, {@code streaks.announce} in BlockcraftAPI's config.yml. */
public record Streaks(List<Integer> announced) {

    public boolean announces(int streak) {
        return announced.contains(streak);
    }
}
