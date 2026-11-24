package me.blockcraft.common.duel;

import java.util.Set;

/** What a challenger picks in the lobby; the Duell server draws the map among the mode's playable ones. */
public record DuelRules(String mode, boolean legacy, int bestOf) {
    public DuelRules {
        if (!Set.of("vs", "sg", "superjump", "sumo", "mlgrush", "throw").contains(mode) || bestOf != 1 && bestOf != 3) {
            throw new IllegalArgumentException("Invalid duel rules");
        }
    }
}
