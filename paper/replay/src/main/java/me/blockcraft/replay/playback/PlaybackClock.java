package me.blockcraft.replay.playback;

final class PlaybackClock {

    private final int duration;
    private int tick;
    private double remainder;
    private double speed = 1;
    private boolean paused;

    PlaybackClock(int duration) {
        this.duration = duration;
    }

    int tick() { return tick; }
    boolean paused() { return paused; }
    double speed() { return speed; }

    void pause() { paused = !paused; }

    boolean speed(double speed) {
        if (speed != 0.5 && speed != 1 && speed != 2) {
            return false;
        }
        this.speed = speed;
        return true;
    }

    boolean seek(int tick) {
        if (tick < 0 || tick > duration) {
            return false;
        }
        this.tick = tick;
        remainder = 0;
        return true;
    }

    void advance() {
        if (paused || tick >= duration) {
            return;
        }
        remainder += speed;
        int elapsed = (int) remainder;
        remainder -= elapsed;
        tick = Math.min(duration, tick + elapsed);
    }
}
