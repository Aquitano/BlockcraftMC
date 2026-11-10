package me.blockcraft.common.task;

import java.util.HashMap;
import java.util.Map;

/** A new request or disconnect invalidates earlier completions for the same key. */
public final class LatestRequests<K> {

    private final Map<K, Object> current = new HashMap<>();

    public synchronized Object begin(K key) {
        var token = new Object();
        current.put(key, token);
        return token;
    }

    public synchronized Object snapshot(K key) {
        return current.computeIfAbsent(key, ignored -> new Object());
    }

    public synchronized boolean isCurrent(K key, Object token) {
        return current.get(key) == token;
    }

    public synchronized boolean finish(K key, Object token) {
        return current.remove(key, token);
    }

    public synchronized void invalidate(K key) {
        current.remove(key);
    }
}
