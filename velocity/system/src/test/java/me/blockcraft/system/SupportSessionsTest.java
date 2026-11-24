package me.blockcraft.system;

import me.blockcraft.system.support.SupportSessions;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupportSessionsTest {

    @Test
    public void assignmentSurvivesReopeningAndRequeuesWhenStaffLeaves() {
        var sessions = new SupportSessions();
        var requester = UUID.randomUUID();
        var staff = UUID.randomUUID();
        assertTrue(sessions.open(requester));
        var opened = sessions.open().getFirst().openedAt();
        assertTrue(sessions.assign(requester, staff));
        assertFalse(sessions.open(requester));
        assertFalse(sessions.assign(requester, UUID.randomUUID()));

        sessions.onDisconnect(staff);
        assertEquals(SupportSessions.State.WAITING, sessions.state(requester));
        assertEquals(opened, sessions.open().getFirst().openedAt());

        assertTrue(sessions.assign(requester, staff));
        var waiting = UUID.randomUUID();
        sessions.open(waiting);
        assertEquals(waiting, sessions.open().getFirst().requester());

        sessions.onDisconnect(requester);
        assertFalse(sessions.assign(requester, staff));
    }

    @Test
    public void onlyOneOfManyConcurrentClaimantsWins() throws Exception {
        var sessions = new SupportSessions();
        var requester = UUID.randomUUID();
        sessions.open(requester);
        int claimants = 32;
        var ready = new CountDownLatch(claimants);
        var start = new CountDownLatch(1);
        var won = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < claimants; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (sessions.assign(requester, UUID.randomUUID())) {
                        won.incrementAndGet();
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }
        assertEquals(1, won.get());
    }
}
