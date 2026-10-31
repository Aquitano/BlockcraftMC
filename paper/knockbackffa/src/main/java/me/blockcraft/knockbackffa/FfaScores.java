package me.blockcraft.knockbackffa;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FfaScores {

    record Score(int kills, int deaths, int streak) {
        static final Score EMPTY = new Score(0, 0, 0);
    }

    record Kill(UUID killer, boolean rewarded) { }

    private record Hit(UUID attacker, long at) { }

    private record Pair(UUID killer, UUID victim) { }

    private final long creditWindow;
    private final long rewardCooldown;
    private final Map<UUID, Score> scores = new HashMap<>();
    private final Map<UUID, Hit> hits = new HashMap<>();
    private final Map<Pair, Long> rewards = new HashMap<>();

    FfaScores(Duration creditWindow, Duration rewardCooldown) {
        this.creditWindow = creditWindow.toNanos();
        this.rewardCooldown = rewardCooldown.toNanos();
    }

    void join(UUID player) {
        scores.putIfAbsent(player, Score.EMPTY);
    }

    Score score(UUID player) {
        return scores.getOrDefault(player, Score.EMPTY);
    }

    void hit(UUID attacker, UUID victim, long now) {
        if (!attacker.equals(victim) && scores.containsKey(attacker) && scores.containsKey(victim)) {
            hits.put(victim, new Hit(attacker, now));
        }
    }

    boolean inCombat(UUID player, long now) {
        var hit = hits.get(player);
        return hit != null && now - hit.at() <= creditWindow && scores.containsKey(hit.attacker());
    }

    Optional<Kill> death(UUID victim, long now) {
        if (!scores.containsKey(victim)) {
            return Optional.empty();
        }
        var previous = score(victim);
        scores.put(victim, new Score(previous.kills(), previous.deaths() + 1, 0));
        var hit = hits.remove(victim);
        if (hit == null || now - hit.at() > creditWindow || !scores.containsKey(hit.attacker())) {
            return Optional.empty();
        }
        var attacker = score(hit.attacker());
        scores.put(hit.attacker(), new Score(attacker.kills() + 1, attacker.deaths(), attacker.streak() + 1));
        rewards.entrySet().removeIf(entry -> now - entry.getValue() >= rewardCooldown);
        boolean rewarded = rewards.putIfAbsent(new Pair(hit.attacker(), victim), now) == null;
        return Optional.of(new Kill(hit.attacker(), rewarded));
    }

    void leave(UUID player) {
        scores.remove(player);
        hits.remove(player);
        hits.values().removeIf(hit -> hit.attacker().equals(player));
    }

    void clearHits() {
        hits.clear();
    }
}
