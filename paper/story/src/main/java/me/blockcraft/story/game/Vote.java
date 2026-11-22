package me.blockcraft.story.game;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** How the party decides a vote among the members who are here; one who is away neither holds it up nor counts. */
final class Vote {

    private Vote() {
    }

    /**
     * The choice once every member {@code here} has cast one in {@code cast}: the majority's, and on a tie the choice
     * of whoever of them joined the party first, in {@code joined} order.
     */
    static Optional<String> outcome(Map<UUID, String> cast, List<UUID> here, Collection<UUID> joined) {
        if (here.isEmpty() || !cast.keySet().containsAll(here)) return Optional.empty();
        var counts = new LinkedHashMap<String, Integer>();
        here.forEach(member -> counts.merge(cast.get(member), 1, Integer::sum));
        int most = Collections.max(counts.values());
        var leading = counts.entrySet().stream().filter(entry -> entry.getValue() == most).map(Map.Entry::getKey).toList();
        if (leading.size() == 1) return Optional.of(leading.getFirst());
        return joined.stream().filter(here::contains).map(cast::get).filter(leading::contains).findFirst().or(() -> Optional.of(leading.getFirst()));
    }
}
