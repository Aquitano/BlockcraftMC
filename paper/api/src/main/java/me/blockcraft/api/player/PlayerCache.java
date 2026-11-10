package me.blockcraft.api.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What each online player has stored per key, readable from chat threads; a key the player has no value for reads as {@code absent}. */
public final class PlayerCache<K, V> {

    private final Map<UUID, Map<K, V>> players = new ConcurrentHashMap<>();
    private final V absent;

    public PlayerCache(V absent) {
        this.absent = absent;
    }

    public V get(UUID player, K key) {
        return players.getOrDefault(player, Map.of()).getOrDefault(key, absent);
    }

    public void set(UUID player, K key, V value) {
        players.compute(player, (ignored, current) -> {
            var updated = current == null ? new HashMap<K, V>() : new HashMap<>(current);
            updated.put(key, value);
            return Map.copyOf(updated);
        });
    }

    /** A change heard while the stored values were being read is newer than what the read returns. */
    public void load(UUID player, Map<K, V> stored) {
        players.compute(player, (ignored, current) -> {
            var loaded = new HashMap<>(stored);
            if (current != null) {
                loaded.putAll(current);
            }
            return Map.copyOf(loaded);
        });
    }

    public void forget(UUID player) {
        players.remove(player);
    }
}
