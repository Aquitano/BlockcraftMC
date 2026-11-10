package me.blockcraft.sternenfront.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class SupportLedger {

    record Rules(int assistTicks, int healingTicks, int assistPoints, double healingPerHealth, int healingLimit) { }
    private record Damage(double amount, long tick) { }

    private static final class Wounds {
        private final Map<UUID, Damage> attackers = new HashMap<>();
        private final Map<UUID, Double> fractions = new HashMap<>();
        private double remaining;
        private long hurtAt;
        private int healingAwarded;
    }

    private final Rules rules;
    private final Map<UUID, Wounds> victims = new HashMap<>();

    SupportLedger(Rules rules) {
        this.rules = rules;
    }

    void damage(UUID victim, Optional<UUID> attacker, double amount, long tick) {
        if (amount <= 0) {
            return;
        }
        var wounds = victims.computeIfAbsent(victim, ignored -> new Wounds());
        if (tick - wounds.hurtAt > rules.healingTicks()) {
            wounds.remaining = 0;
        }
        wounds.remaining += amount;
        wounds.hurtAt = tick;
        attacker.filter(id -> !id.equals(victim)).ifPresent(id -> {
            var previous = wounds.attackers.get(id);
            double total = previous != null && tick - previous.tick() <= rules.assistTicks() ? previous.amount() : 0;
            wounds.attackers.put(id, new Damage(total + amount, tick));
        });
    }

    int heal(UUID victim, Optional<UUID> healer, double amount, long tick) {
        var wounds = victims.get(victim);
        if (wounds == null || amount <= 0 || tick - wounds.hurtAt > rules.healingTicks()) {
            return 0;
        }
        double restored = Math.min(amount, wounds.remaining);
        wounds.remaining -= restored;
        if (healer.isEmpty() || healer.get().equals(victim)) {
            return 0;
        }
        var id = healer.get();
        double earned = restored * rules.healingPerHealth() + wounds.fractions.getOrDefault(id, 0.0);
        int points = Math.min((int) earned, Math.max(0, rules.healingLimit() - wounds.healingAwarded));
        wounds.fractions.put(id, earned - Math.floor(earned));
        wounds.healingAwarded += points;
        return points;
    }

    Map<UUID, Integer> killed(UUID victim, Optional<UUID> killer, int killPoints, int humanKillPoints, long tick) {
        var wounds = victims.remove(victim);
        if (wounds == null) {
            return Map.of();
        }
        int points = (int) Math.min(rules.assistPoints(), (long) rules.assistPoints() * killPoints / Math.max(1, humanKillPoints));
        var awards = new HashMap<UUID, Integer>();
        wounds.attackers.forEach((id, damage) -> {
            if (points > 0 && killer.filter(id::equals).isEmpty() && damage.amount() >= 2 && tick - damage.tick() <= rules.assistTicks()) {
                awards.put(id, points);
            }
        });
        return Map.copyOf(awards);
    }

    void forgetVictim(UUID victim) {
        victims.remove(victim);
    }

    void forget(UUID player) {
        victims.remove(player);
        victims.values().forEach(wounds -> {
            wounds.attackers.remove(player);
            wounds.fractions.remove(player);
        });
    }

    void clear() {
        victims.clear();
    }
}
