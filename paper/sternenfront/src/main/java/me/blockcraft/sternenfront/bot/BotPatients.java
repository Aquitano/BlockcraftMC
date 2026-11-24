package me.blockcraft.sternenfront.bot;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

final class BotPatients {

    record Patient(UUID id, double healthShare, boolean healable) { }

    private UUID selected;
    private long pursuitUntil;
    private final Map<UUID, Long> retryAt = new HashMap<>();

    UUID selected() {
        return selected;
    }

    Optional<UUID> choose(List<Patient> patients, long tick, BotConfig.Medic config, Predicate<UUID> reachable) {
        retryAt.values().removeIf(until -> tick >= until);
        var healable = patients.stream().filter(Patient::healable)
                .min(Comparator.comparingDouble(Patient::healthShare));
        if (healable.isPresent()) {
            selected = healable.get().id();
            pursuitUntil = tick + config.pursuitTicks();
            return Optional.of(selected);
        }
        if (selected != null && tick >= pursuitUntil) {
            fail(tick, config.retryTicks());
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            var candidates = patients.stream().filter(patient -> !retryAt.containsKey(patient.id())).toList();
            var next = candidates.stream().filter(patient -> patient.id().equals(selected)).findFirst()
                    .or(() -> candidates.stream().min(Comparator.comparingDouble(Patient::healthShare)));
            if (next.isEmpty()) {
                selected = null;
                return Optional.empty();
            }
            if (!next.get().id().equals(selected)) {
                selected = next.get().id();
                pursuitUntil = tick + config.pursuitTicks();
            }
            if (reachable.test(selected)) {
                return Optional.of(selected);
            }
            fail(tick, config.retryTicks());
        }
        return Optional.empty();
    }

    private void fail(long tick, int retryTicks) {
        retryAt.put(selected, tick + retryTicks);
        selected = null;
    }

    void clear() {
        selected = null;
        pursuitUntil = 0;
        retryAt.clear();
    }
}
