package me.blockcraft.system.ranked;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Who meets whom in the ranked queue: neighbours by rating, once the gap between them fits what both accept. How
 * wide a player searches grows with how long they have waited.
 */
final class Matchmaking {

    record Seeker(UUID player, int rating, Instant since) {
    }

    record Pair(Seeker first, Seeker second) {
    }

    private Matchmaking() {
    }

    static int range(Seeker seeker, Instant now, RankedQueue.Settings settings) {
        long waited = Math.max(0, Duration.between(seeker.since(), now).toSeconds());
        return (int) Math.min(settings.maxRange(), settings.baseRange() + waited * settings.rangePerSecond());
    }

    static List<Pair> pairs(Collection<Seeker> seekers, Instant now, RankedQueue.Settings settings) {
        var sorted = seekers.stream().sorted(Comparator.comparingInt(Seeker::rating)).toList();
        var pairs = new ArrayList<Pair>();
        int index = 0;
        while (index + 1 < sorted.size()) {
            var first = sorted.get(index);
            var second = sorted.get(index + 1);
            if (second.rating() - first.rating() <= Math.min(range(first, now, settings), range(second, now, settings))) {
                pairs.add(new Pair(first, second));
                index += 2;
            } else {
                index++;
            }
        }
        return pairs;
    }
}
