package me.blockcraft.common.task;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LatestRequestsTest {

    @Test
    public void staleCompletionCannotConsumeNewRequest() {
        var requests = new LatestRequests<String>();
        var first = requests.begin("player");
        var second = requests.begin("player");
        assertFalse(requests.finish("player", first));
        assertTrue(requests.finish("player", second));
        assertFalse(requests.finish("player", second));
    }

    @Test
    public void disconnectInvalidatesTheOldSession() {
        var requests = new LatestRequests<String>();
        var old = requests.begin("player");
        requests.invalidate("player");
        var rejoined = requests.begin("player");
        assertFalse(requests.finish("player", old));
        assertTrue(requests.finish("player", rejoined));
    }
}
