package me.blockcraft.api.notification;

import org.junit.Test;

import static me.blockcraft.api.notification.NoticeQueue.Priority;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NoticeQueueTest {
    @Test
    public void importantTitlesHoldQueuedRewardsAndDropInterruptedTitles() {
        var queue = new NoticeQueue<String>();
        queue.offer("achievement", Priority.REWARD, 0, 100, 200);
        assertEquals("achievement", queue.next(0).orElseThrow().value);
        queue.offer("round result", Priority.IMPORTANT, 1, 60, 200);
        assertEquals("round result", queue.next(1).orElseThrow().value);
        assertTrue(queue.next(61).isEmpty());

        queue.offer("countdown", Priority.IMPORTANT, 100, 60, 200);
        queue.offer("new achievement", Priority.REWARD, 100, 40, 200);
        assertEquals("countdown", queue.next(100).orElseThrow().value);
        assertEquals("new achievement", queue.next(160).orElseThrow().value);
        assertTrue(queue.next(200).isEmpty());
    }

    @Test
    public void onlyTheLatestPendingStatusSurvivesAndStaleNoticesExpire() {
        var queue = new NoticeQueue<String>();
        queue.offer("purchase", Priority.FEEDBACK, 0, 40, 200);
        queue.next(0);
        queue.offer("old count", Priority.STATUS, 1, 25, 25);
        queue.offer("new count", Priority.STATUS, 20, 25, 25);
        assertEquals("purchase", queue.next(20).orElseThrow().value);
        assertEquals("new count", queue.next(40).orElseThrow().value);
        assertTrue(queue.next(65).isEmpty());
        queue.offer("expired", Priority.STATUS, 100, 25, 25);
        assertTrue(queue.next(125).isEmpty());
    }
}
