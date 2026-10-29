package me.blockcraft.replay.recording;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PublicationGateTest {

    @Test
    public void limitedRecordingWaitsForItsMatchToEndAndRematchHasItsOwnGate() {
        var first = new PublicationGate<String>();
        var second = new PublicationGate<String>();
        var published = first.ready();
        first.completed("DURATION_LIMIT");
        assertFalse(published.isDone());

        second.ended();
        assertFalse(published.isDone());

        first.ended();
        assertEquals("DURATION_LIMIT", published.join());
        assertFalse(second.ready().isDone());
        second.completed("MATCH_END");
        assertEquals("MATCH_END", second.ready().join());
    }
}
