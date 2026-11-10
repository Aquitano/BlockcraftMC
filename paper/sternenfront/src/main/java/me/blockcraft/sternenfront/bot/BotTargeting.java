package me.blockcraft.sternenfront.bot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class BotTargeting {

    record Target(UUID id, double distanceSquared) { }

    private BotTargeting() { }

    static Optional<UUID> choose(List<Target> targets, UUID current) {
        var nearest = targets.stream().min(Comparator.comparingDouble(Target::distanceSquared));
        if (nearest.isEmpty()) {
            return Optional.empty();
        }
        var kept = targets.stream().filter(target -> target.id().equals(current))
                .filter(target -> target.distanceSquared() <= nearest.get().distanceSquared() * 2).findFirst();
        return kept.or(() -> nearest).map(Target::id);
    }
}
