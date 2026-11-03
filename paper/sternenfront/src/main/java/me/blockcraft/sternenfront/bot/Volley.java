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

    boolean next(long tick) {
        if (left == 0 || tick < next) {
            return false;
        }
        left--;
        next += spacing;
        return true;
    }

    boolean active() {
        return left > 0;
    }

    void clear() {
        left = 0;
    }
}
