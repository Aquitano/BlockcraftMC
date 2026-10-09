package me.blockcraft.system.network;

import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LagWatchTest {

    @Test
    public void lagCountsOnlyAfterEnoughSlowPollsInARow() {
        var watch = new LagWatch(50, 3);

        assertTrue(watch.poll(List.of(server(80))).isEmpty());
        assertTrue(watch.poll(List.of(server(80))).isEmpty());
        assertTrue(watch.poll(List.of(server(20))).isEmpty());
        assertTrue(watch.poll(List.of(server(80))).isEmpty());
        assertTrue(watch.poll(List.of(server(80))).isEmpty());
        assertEquals(List.of(new LagWatch.Change(server(80), true)), watch.poll(List.of(server(80))));
    }

    @Test
    public void anUnannouncedChangeStaysPendingAndRecoveryFollowsTheAnnouncement() {
        var watch = new LagWatch(50, 1);

        assertEquals(1, watch.poll(List.of(server(80))).size());
        var lagging = watch.poll(List.of(server(80)));
        assertEquals(List.of(new LagWatch.Change(server(80), true)), lagging);
        watch.announced(lagging);

        assertTrue(watch.poll(List.of(server(80))).isEmpty());
        assertEquals(List.of(new LagWatch.Change(server(20), false)), watch.poll(List.of(server(20))));
    }

    private static ServerRecord server(double mspt) {
        return new ServerRecord(new ServerIdentity("quicksg-1", "quicksg", "127.0.0.1", 25566),
                new ServerStatus(ServerState.LOBBY, 4, 8, 20, mspt));
    }
}
