package me.blockcraft.api.game;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class Curiosities {

    public enum Kind {
        BOW("bow-blocks", 40), JUMPS("jumps", 100), HALF_HEART("half-heart-seconds", 10),
        ROD("rod-hits", 10), EMERALDS("emeralds-mined", 100);

        private final String key;
        private final int minimum;

        Kind(String key, int minimum) {
            this.key = key;
            this.minimum = minimum;
        }

        public String key() {
            return key;
        }
    }

    public record Fact(UUID player, Kind kind, long value) { }

    private final Map<Kind, Integer> minimums;
    private final Map<UUID, Map<Kind, Long>> values = new HashMap<>();
    private final Map<UUID, Long> woundedSince = new HashMap<>();

    public Curiosities(Map<Kind, Integer> minimums) {
        this.minimums = Map.copyOf(minimums);
    }

    public static Map<Kind, Integer> minimums(Map<String, Object> config) {
        var minimums = new EnumMap<Kind, Integer>(Kind.class);
        for (var kind : Kind.values()) {
            int minimum = config.get(kind.key) instanceof Number number ? number.intValue() : kind.minimum;
            if (minimum > 0) {
                minimums.put(kind, minimum);
            }
        }
        return minimums;
    }

    public void record(UUID player, Kind kind, long amount) {
        if (amount <= 0 || !minimums.containsKey(kind)) {
            return;
        }
        values.computeIfAbsent(player, ignored -> new EnumMap<>(Kind.class))
                .merge(kind, amount, kind == Kind.BOW ? Math::max : Long::sum);
    }

    public void health(UUID player, boolean wounded, long now) {
        if (!minimums.containsKey(Kind.HALF_HEART)) {
            return;
        }
        if (wounded) {
            woundedSince.putIfAbsent(player, now);
        } else {
            var started = woundedSince.remove(player);
            if (started != null) {
                record(player, Kind.HALF_HEART, Math.max(0, now - started));
            }
        }
    }

    public void finish(long now) {
        for (var player : List.copyOf(woundedSince.keySet())) {
            health(player, false, now);
        }
    }

    public Optional<Fact> select(Predicate<UUID> eligible) {
        return values.entrySet().stream().filter(entry -> eligible.test(entry.getKey()))
                .flatMap(entry -> entry.getValue().entrySet().stream().map(value -> new Fact(entry.getKey(), value.getKey(),
                        value.getKey() == Kind.HALF_HEART ? TimeUnit.NANOSECONDS.toSeconds(value.getValue()) : value.getValue())))
                .filter(fact -> fact.value() >= minimums.get(fact.kind()))
                .max(Comparator.comparingDouble((Fact fact) -> (double) fact.value() / minimums.get(fact.kind()))
                        .thenComparing(fact -> fact.kind().ordinal()).thenComparing(fact -> fact.player().toString()));
    }
}
