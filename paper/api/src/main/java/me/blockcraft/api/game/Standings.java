package me.blockcraft.api.game;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Who has beaten whom how often among one round's players, as loaded when it started and counted on from there. */
final class Standings {

    /** The score between a player and a rival from the player's side: kills won against them, and lost to them. */
    record Standing(int won, int lost) {

        int lead() {
            return lost - won;
        }
    }

    private record Pair(UUID killer, UUID victim) {
    }

    private final Map<Pair, Integer> kills = new HashMap<>();

    Standings(List<RivalryRepository.Rivalry> rivalries) {
        rivalries.forEach(rivalry -> kills.put(new Pair(rivalry.killer(), rivalry.victim()), rivalry.kills()));
    }

    void count(UUID killer, UUID victim) {
        kills.merge(new Pair(killer, victim), 1, Integer::sum);
    }

    Standing between(UUID player, UUID rival) {
        return new Standing(kills.getOrDefault(new Pair(player, rival), 0), kills.getOrDefault(new Pair(rival, player), 0));
    }

    /**
     * The opponent who has beaten the player at least {@code lead} times more often than the other way round; of
     * several, the one furthest ahead, and of those the one with the most wins over the player.
     */
    Optional<UUID> nemesis(UUID player, Collection<UUID> opponents, int lead) {
        return opponents.stream()
                .filter(opponent -> !opponent.equals(player) && between(player, opponent).lead() >= lead)
                .max(Comparator.<UUID>comparingInt(opponent -> between(player, opponent).lead())
                        .thenComparingInt(opponent -> between(player, opponent).lost()));
    }
}
