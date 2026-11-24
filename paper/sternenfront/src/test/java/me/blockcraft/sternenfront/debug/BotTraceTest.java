package me.blockcraft.sternenfront.debug;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotTraceTest {
    @Test
    public void keepsBoundedHistoryAndFinishesWithThePostClickFramesWithoutRestarting() {
        var trace = new BotTrace<String>(3, 2);
        for (int tick = 0; tick <= 4; tick++) {
            trace.sample(tick, "frame-" + tick);
        }
        assertTrue(trace.start(4));
        trace.sample(4, "duplicate-click-tick");
        assertFalse(trace.start(5));
        trace.sample(5, "frame-5");
        assertTrue(trace.finish(5, false).isEmpty());
        trace.sample(6, "frame-6");
        assertEquals(List.of("frame-2", "frame-3", "frame-4", "frame-5", "frame-6"), trace.finish(6, false).orElseThrow());
        assertTrue(trace.finish(7, false).isEmpty());
    }

    @Test
    public void preservesAPartialCaptureWhenTheBotDisappears() {
        var trace = new BotTrace<String>(100, 200);
        trace.sample(10, "before");
        trace.start(10);
        trace.sample(11, "after");
        assertEquals(List.of("before", "after"), trace.finish(12, true).orElseThrow());
        assertTrue(trace.finish(210, true).isEmpty());
    }
}
