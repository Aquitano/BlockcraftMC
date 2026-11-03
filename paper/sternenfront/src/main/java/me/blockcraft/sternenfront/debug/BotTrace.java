package me.blockcraft.sternenfront.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class BotTrace<T> {
    private record Sample<T>(long tick, T value) { }

    private final int historyTicks;
    private final int captureTicks;
    private final ArrayDeque<Sample<T>> history = new ArrayDeque<>();
    private List<T> recording;
    private long markedAt;
    private long finishesAt;

    BotTrace(int historyTicks, int captureTicks) {
        this.historyTicks = historyTicks;
        this.captureTicks = captureTicks;
    }

    void sample(long tick, T value) {
        if (!history.isEmpty() && history.getLast().tick() == tick) {
            return;
        }
        history.addLast(new Sample<>(tick, value));
        while (!history.isEmpty() && tick - history.getFirst().tick() >= historyTicks) {
            history.removeFirst();
        }
        if (recording != null && tick > markedAt && tick <= finishesAt) {
            recording.add(value);
        }
    }

    boolean start(long tick) {
        if (recording != null) {
            return false;
        }
        recording = new ArrayList<>(history.stream().map(Sample::value).toList());
        markedAt = tick;
        finishesAt = tick + captureTicks;
        return true;
    }

    Optional<List<T>> finish(long tick, boolean ended) {
        if (recording == null || !ended && tick < finishesAt) {
            return Optional.empty();
        }
        var result = List.copyOf(recording);
        recording = null;
        return Optional.of(result);
    }
}
