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
        clock.pause();
        clock.advance();
        assertEquals(0, clock.tick());
        clock.pause();
        clock.advance();
        assertEquals(1, clock.tick());
        assertTrue(clock.speed(2));
        clock.advance();
        assertEquals(3, clock.tick());
    }

    @Test
    public void seekStaysInsideRecordingAndResetsFractionalTime() {
        var clock = new PlaybackClock(20);
        clock.speed(0.5);
        clock.advance();
        assertTrue(clock.seek(18));
        clock.advance();
        assertEquals(18, clock.tick());
        assertFalse(clock.seek(-1));
        assertFalse(clock.seek(21));
        assertFalse(clock.speed(Double.NaN));
        assertFalse(clock.speed(10));
        assertEquals(18, clock.tick());
        clock.speed(2);
        clock.advance();
        clock.advance();
        assertEquals(20, clock.tick());
        clock.pause();
        assertTrue(clock.seek(0));
        assertTrue(clock.paused());
    }
}
