package me.blockcraft.sternenfront.bot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class BotTargeting {

    /** {@code attacker} marks an enemy that hit the bot recently. */
    record Target(UUID id, double distanceSquared, boolean attacker) {

        /** An attacker weighs as if it stood at half its distance. */
        double weighed() {
            return attacker ? distanceSquared / 4 : distanceSquared;
        }
    }

    private BotTargeting() { }

    static Optional<UUID> choose(List<Target> targets, UUID current) {
        var nearest = targets.stream().min(Comparator.comparingDouble(Target::weighed));
        if (nearest.isEmpty()) {
            return Optional.empty();
        }
        var kept = targets.stream().filter(target -> target.id().equals(current))
                .filter(target -> target.weighed() <= nearest.get().weighed() * 2).findFirst();
        return kept.or(() -> nearest).map(Target::id);
    }
}
