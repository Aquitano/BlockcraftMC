package me.blockcraft.api.xp;

/**
 * Level curve: reaching the next level costs {@code step} more XP than the previous one did, so the total for
 * level {@code n} is {@code step * n * (n - 1) / 2}. Level 1 is 0 XP; with a step of 100, level 2 is 100, level 3
 * is 300, level 10 is 4500.
 */
public record Levels(long step) {

    /** Where a total sits on the curve: the level, the XP earned since reaching it and the XP the next level costs. */
    public record Progress(int level, long into, long needed) {
    }

    public long totalFor(int level) {
        return step * level * (level - 1L) / 2;
    }

    public int levelFor(long xp) {
        int level = (int) ((1 + Math.sqrt(1 + 8.0 * xp / step)) / 2);
        while (totalFor(level + 1) <= xp) {
            level++;
        }
        while (level > 1 && totalFor(level) > xp) {
            level--;
        }
        return level;
    }

    public Progress progress(long xp) {
        int level = levelFor(xp);
        long reached = totalFor(level);
        return new Progress(level, xp - reached, totalFor(level + 1) - reached);
    }
}
