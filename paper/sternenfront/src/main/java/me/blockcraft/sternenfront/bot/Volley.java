package me.blockcraft.sternenfront.bot;

final class Volley {

    private int left;
    private int spacing;
    private long next;

    void start(long tick, int bolts, int spacing) {
        this.left = bolts;
        this.spacing = spacing;
        this.next = tick;
    }

    /** A volley held back past its next bolt goes on at its spacing from {@code tick}, not with every overdue bolt at once. */
    boolean next(long tick) {
        if (left == 0 || tick < next) {
            return false;
        }
        left--;
        next = Math.max(next, tick) + spacing;
        return true;
    }

    boolean active() {
        return left > 0;
    }

    void clear() {
        left = 0;
    }
}
