package me.blockcraft.lobby.activity;

public final class ParkourRun {

    private final long started;
    private int checkpoint;
    private int falls;

    public ParkourRun(long started) {
        this.started = started;
    }

    public boolean reach(int next) {
        if (next != checkpoint + 1) {
            return false;
        }
        checkpoint = next;
        return true;
    }

    public int checkpoint() {
        return checkpoint;
    }

    public int fall() {
        return ++falls;
    }

    public long millis(long now) {
        return Math.max(0, (now - started) / 1_000_000);
    }

    public static String time(long millis) {
        return "%02d:%02d.%d".formatted(millis / 60_000, millis / 1000 % 60, millis / 100 % 10);
    }
}
