package me.blockcraft.api.notification;

final class RewardBatch {
    record Xp(long amount, int level) {}

    private long amount;
    private int level;
    private long due;

    void add(int gained, int reachedLevel, long now, long window) {
        if (amount == 0) {
            due = now + window;
        }
        amount += gained;
        level = Math.max(level, reachedLevel);
    }

    Xp take(long now) {
        if (amount == 0 || now < due) {
            return null;
        }
        var result = new Xp(amount, level);
        amount = 0;
        level = 0;
        return result;
    }

    boolean isEmpty() {
        return amount == 0;
    }
}
