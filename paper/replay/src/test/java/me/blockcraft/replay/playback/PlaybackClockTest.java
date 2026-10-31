package me.blockcraft.replay.playback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackClockTest {

    @Test
    public void halfSpeedAndPauseKeepTheRecordedTickTiming() {
        var clock = new PlaybackClock(20);
        assertTrue(clock.speed(0.5));
        clock.advance();
        assertEquals(0, clock.tick());
        assertEquals(0.5, clock.time(), 0);
        clock.toggle();
        clock.advance();
        assertEquals(0, clock.tick());
        clock.toggle();
        clock.advance();
        assertEquals(1, clock.tick());
        assertTrue(clock.speed(2));
        clock.advance();
        assertEquals(3, clock.tick());
        assertFalse(clock.speed(Double.NaN));
        assertFalse(clock.speed(10));
    }

    @Test
    public void seekStaysInsideTheRecordingAndPlayingOnAtTheEndStartsOver() {
        var clock = new PlaybackClock(20);
        clock.seek(-5);
        assertEquals(0, clock.tick());
        clock.seek(25);
        assertEquals(20, clock.tick());
        assertTrue(clock.finished());
        clock.toggle();
        assertEquals(0, clock.tick());
        assertFalse(clock.paused());
        clock.seek(20);
        clock.pause();
        clock.toggle();
        assertEquals(0, clock.tick());
        assertFalse(clock.paused());
    }
}
