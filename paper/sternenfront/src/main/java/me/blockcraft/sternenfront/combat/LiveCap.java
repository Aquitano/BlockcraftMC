package me.blockcraft.sternenfront.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The effects alive right now, oldest first, never more than {@code limit}. */
final class LiveCap<T> {

    private final int limit;
    private final Deque<T> live = new ArrayDeque<>();

    LiveCap(int limit) {
        this.limit = Math.max(1, limit);
    }

    boolean hasRoom() {
        return live.size() < limit;
    }

    /** Takes the effect in and hands back the oldest one it pushed out, which the caller must end. */
    Optional<T> admit(T effect) {
        var evicted = hasRoom() ? Optional.<T>empty() : Optional.of(live.removeFirst());
        live.addLast(effect);
        return evicted;
    }

    void removeIf(Predicate<? super T> finished) {
        live.removeIf(finished);
    }

    void drain(Consumer<? super T> end) {
        live.forEach(end);
        live.clear();
    }

    int size() {
        return live.size();
    }
}
