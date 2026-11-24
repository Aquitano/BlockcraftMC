package me.blockcraft.api.afk;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WakeAttempts {

    public record Poker(UUID player, String name, int count) { }

    private final Map<UUID, Map<UUID, Poker>> attempts = new HashMap<>();

    public void poke(UUID sleeper, UUID poker, String name) {
        attempts.computeIfAbsent(sleeper, ignored -> new HashMap<>()).compute(poker, (id, previous) ->
                new Poker(id, name, previous == null ? 1 : previous.count() + 1));
    }

    public Optional<Poker> take(UUID sleeper) {
        var pokers = attempts.remove(sleeper);
        return pokers == null ? Optional.empty() : pokers.values().stream()
                .max(Comparator.comparingInt(Poker::count).thenComparing(poker -> poker.player().toString()));
    }

    public void clear(UUID sleeper) {
        attempts.remove(sleeper);
    }
}
