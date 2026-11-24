package me.blockcraft.api.notification;

import java.util.EnumMap;
import java.util.Optional;

final class NoticeQueue<T> {
    enum Priority { STATUS, REWARD, FEEDBACK, IMPORTANT }

    static final class Notice<T> {
        final T value;
        final Priority priority;
        final long duration;
        final long deadline;
        long until;

        Notice(T value, Priority priority, long duration, long deadline) {
            this.value = value;
            this.priority = priority;
            this.duration = duration;
            this.deadline = deadline;
        }
    }

    private Notice<T> active;
    private final EnumMap<Priority, Notice<T>> pending = new EnumMap<>(Priority.class);

    void offer(T value, Priority priority, long now, long duration, long maxWait) {
        pending.put(priority, new Notice<>(value, priority, duration, now + maxWait));
    }

    Optional<Notice<T>> next(long now) {
        pending.values().removeIf(notice -> notice.until == 0 ? now >= notice.deadline : now >= notice.until);
        Notice<T> selected = null;
        for (var notice : pending.values()) {
            selected = notice;
        }
        if (active != null && selected != active) {
            pending.remove(active.priority, active);
        }
        active = selected;
        if (selected != null && selected.until == 0) {
            selected.until = now + selected.duration;
        }
        return Optional.ofNullable(selected);
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }
}
