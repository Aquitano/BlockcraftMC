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
    boolean finished() { return tick >= duration; }

    /** The recorded moment shown, between two ticks while playing slower than recorded. */
    double time() {
        return Math.min(duration, tick + remainder);
    }

    /** Pauses or resumes; at the end of the recording, resuming starts it over. */
    void toggle() {
        if (paused || !finished()) {
            paused = !paused;
        }
        if (!paused && finished()) {
            seek(0);
        }
    }

    void pause() { paused = true; }

    boolean speed(double speed) {
        if (speed != 0.5 && speed != 1 && speed != 2) {
            return false;
        }
        this.speed = speed;
        return true;
    }

    void seek(int tick) {
        this.tick = Math.clamp(tick, 0, duration);
        remainder = 0;
    }

    void advance() {
        if (paused || finished()) {
            return;
        }
        remainder += speed;
        int elapsed = (int) remainder;
        remainder -= elapsed;
        tick = Math.min(duration, tick + elapsed);
    }
}
