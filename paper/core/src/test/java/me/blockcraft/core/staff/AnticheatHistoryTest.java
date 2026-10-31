package me.blockcraft.core.staff;

import me.blockcraft.common.anticheat.AnticheatEvidence;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnticheatHistoryTest {

    @Test(timeout = 5000)
    public void aBlockedWriterKeepsOnlyItsBoundedQueueAndOneWarning() throws Exception {
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var written = new AtomicInteger();
        var warnings = new AtomicInteger();
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { warnings.incrementAndGet(); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var history = new AnticheatHistory(evidence -> {
            writing.countDown();
            await(release);
            written.incrementAndGet();
        }, () -> { }, 1, logger);
        try {
            assertTrue(history.record(evidence()));
            assertTrue(writing.await(1, TimeUnit.SECONDS));
            assertTrue(history.record(evidence()));
            assertFalse(history.record(evidence()));
            assertFalse(history.record(evidence()));
            assertEquals(1, warnings.get());
        } finally {
            release.countDown();
            history.close();
        }
        assertEquals(2, written.get());
        assertFalse(history.record(evidence()));
    }

    @Test(timeout = 5000)
    public void retentionFailureStopsNewRecordsUntilItCanTrimAgain() {
        var trims = new AtomicInteger();
        var written = new AtomicInteger();
        var history = new AnticheatHistory(evidence -> written.incrementAndGet(), () -> {
            if (trims.incrementAndGet() == 1) {
                throw new IllegalStateException("Database unavailable");
            }
        }, 2, Logger.getAnonymousLogger());
        history.record(evidence());
        history.record(evidence());
        history.close();
        assertEquals(2, trims.get());
        assertEquals(1, written.get());
    }

    @Test(timeout = 5000)
    public void shutdownDoesNotWaitForAnUnresponsiveDatabase() throws Exception {
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new CountDownLatch(1);
        var history = new AnticheatHistory(evidence -> {
            writing.countDown();
            boolean waiting = true;
            while (waiting) {
                try {
                    release.await();
                    waiting = false;
                } catch (InterruptedException ignored) {
                }
            }
        }, () -> { }, 1, Logger.getAnonymousLogger());
        try {
            history.record(evidence());
            assertTrue(writing.await(1, TimeUnit.SECONDS));
            var stopping = new Thread(() -> {
                history.close();
                closed.countDown();
            });
            stopping.start();
            assertTrue(closed.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            history.close();
        }
    }

    @Test
    public void configurationCannotMakeRetentionOrQueuesUnbounded() {
        assertEquals(new AnticheatHistory.Settings(30, 100, 4096), AnticheatHistory.Settings.fromYaml(Map.of(
                "history-days", Integer.MAX_VALUE, "history-max-entries", -1, "history-pending-capacity", Integer.MAX_VALUE)));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static AnticheatEvidence evidence() {
        return new AnticheatEvidence(UUID.randomUUID(), "Account", "bw-1", Instant.now(),
                "Simulation", "Movement", "2", "offset=0.1", Optional.empty());
    }
}
