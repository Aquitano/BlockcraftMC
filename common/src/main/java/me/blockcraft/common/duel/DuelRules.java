package me.blockcraft.common.duel;

import java.util.Set;

public record DuelRules(String mode, int map, boolean legacy, boolean rated, int bestOf) {
    public DuelRules {
        if (!Set.of("vs", "sg", "superjump", "sumo", "mlgrush", "throw").contains(mode)
                || map < 1 || map > 1000 || bestOf != 1 && bestOf != 3) {
            throw new IllegalArgumentException("Invalid duel rules");
        }
    }
}
