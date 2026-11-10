package me.blockcraft.api.notification;

import me.blockcraft.api.sound.Sounds;

final class RewardSounds {
    private final long window;
    private final long gap;
    private final long lifetime;
    private Sounds pending;
    private long due;
    private long expires;
    private long ready;

    RewardSounds(long window, long gap, long lifetime) {
        this.window = window;
        this.gap = gap;
        this.lifetime = lifetime;
    }

    void offer(Sounds sound, long now) {
        if (pending == null) {
            due = now + window;
        }
        if (pending == null || rank(sound) >= rank(pending)) {
            pending = sound;
            expires = now + lifetime;
        }
    }

    Sounds take(long now) {
        if (pending == null) {
            return null;
        }
        if (now >= expires) {
            pending = null;
            return null;
        }
        if (now < due || now < ready) {
            return null;
        }
        var sound = pending;
        pending = null;
        ready = now + gap;
        return sound;
    }

    void blockUntil(long until) {
        pending = null;
        ready = Math.max(ready, until);
    }

    boolean isIdle(long now) {
        return pending == null && now >= ready;
    }

    private static int rank(Sounds sound) {
        return switch (sound) {
            case WIN, LOSE -> 5;
            case ACHIEVEMENT, SECRET -> 4;
            case LEVEL_UP -> 3;
            case PURCHASE -> 2;
            default -> 1;
        };
    }
}
