package me.blockcraft.api.ignore;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class IgnoreCache {

    private final Map<UUID, Map<UUID, Boolean>> players = new ConcurrentHashMap<>();

    boolean ignores(UUID player, UUID other) {
        return players.getOrDefault(player, Map.of()).getOrDefault(other, false);
    }

    void set(UUID player, UUID other, boolean ignoring) {
        players.compute(player, (ignored, current) -> {
            var updated = current == null ? new HashMap<UUID, Boolean>() : new HashMap<>(current);
            updated.put(other, ignoring);
            return Map.copyOf(updated);
        });
    }

    /** A change the proxy reported while the table was being read is newer than what the read returns. */
    void load(UUID player, Collection<UUID> stored) {
        players.compute(player, (ignored, current) -> {
            var loaded = new HashMap<UUID, Boolean>();
            stored.forEach(other -> loaded.put(other, true));
            if (current != null) {
                loaded.putAll(current);
            }
            return Map.copyOf(loaded);
        });
    }

    void forget(UUID player) {
        players.remove(player);
    }
}
