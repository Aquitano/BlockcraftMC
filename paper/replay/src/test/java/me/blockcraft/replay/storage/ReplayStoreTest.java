package me.blockcraft.replay.storage;

import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ReplayStoreTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private static final ReplayLimits LIMITS = new ReplayLimits(2, 1200, 4, 16, 8, 1, 1,
            64, 2, 10_000, 4_000_000, 8_000_000, 2, 4096);

    @Test
    public void streamsAllEventKindsAndPublishesOnlyAfterFinish() throws Exception {
        var header = header();
        UUID player = header.participants().getFirst().id();
        UUID world = UUID.randomUUID();
        var actor = new ReplayEvent.Actor(player, world, 1.5, 65, -3, 90, 10, "STANDING",
                List.of(new byte[]{1, 2}, new byte[0], new byte[0], new byte[0], new byte[0], new byte[0]));
        try (var store = store(LIMITS)) {
            var writer = store.begin(header);
            assertTrue(writer.offer(() -> new ReplayEvent.Frame(0, List.of(actor))));
            assertTrue(writer.offer(() -> new ReplayEvent.Chunk(0, world, -1, 2, -64, 16,
                    List.of("minecraft:air"), new int[4096])));
            assertFalse(Files.exists(store.path(header.id())));
            var saved = writer.finish(10, "completed").get(5, TimeUnit.SECONDS);
            assertEquals(Files.size(store.path(header.id())), saved.bytes());
            assertEquals(header, saved.header());
            assertEquals(saved, store.inspect(header.id()));
            assertFalse(writer.offer(() -> new ReplayEvent.Frame(1, List.of())));
            assertSame(writer.finish(20, "ignored"), writer.finish(30, "ignored"));
            var loaded = store.load(header.id());
            assertEquals(header, loaded.header());
            assertEquals(10, loaded.durationTicks());
            assertEquals("completed", loaded.endReason());
            var frame = (ReplayEvent.Frame) loaded.events().getFirst();
            assertEquals(world, frame.actors().getFirst().world());
            assertArrayEquals(new byte[]{1, 2}, frame.actors().getFirst().equipment().getFirst());
            var chunk = (ReplayEvent.Chunk) loaded.events().get(1);
            assertEquals(-64, chunk.minY());
            assertEquals(4096, chunk.blocks().length);
            assertFalse(Files.exists(store.path(header.id()).resolveSibling(header.id() + ".part")));

            var next = header();
            var nextWriter = store.begin(next);
            assertTrue(nextWriter.offer(() -> new ReplayEvent.Blocks(1,
                    List.of(new ReplayEvent.Block(world, 1, 2, 3, "minecraft:stone")))));
            nextWriter.finish(2, "completed").get(5, TimeUnit.SECONDS);
            assertEquals(1, store.load(next.id()).events().size());
        }
    }

    @Test
    public void rejectsTruncationTrailingBytesAndExcessDecodedBudget() throws Exception {
        var header = header();
        try (var store = store(LIMITS)) {
            store.begin(header).finish(0, "completed").get(5, TimeUnit.SECONDS);
            byte[] valid = Files.readAllBytes(store.path(header.id()));
            Files.write(store.path(header.id()), Arrays.copyOf(valid, valid.length - 1));
            assertThrows(IOException.class, () -> store.load(header.id()));
            Files.write(store.path(header.id()), Arrays.copyOf(valid, valid.length + 1));
            assertThrows(IOException.class, () -> store.load(header.id()));
            Files.write(store.path(header.id()), valid);
            var small = new ReplayLimits(2, 1200, 4, 16, 8, 1, 1, 64, 2, 10_000,
                    4_000_000, 16, 2, 4096);
            try (var reader = store(small)) {
                assertThrows(IOException.class, () -> reader.load(header.id()));
            }
        }
    }

    @Test
    public void keepsARecordingThatOutgrowsItsObjectBudgetAsPartial() throws Exception {
        var header = header();
        var tenObjects = new ReplayLimits(2, 1200, 4, 16, 8, 1, 1, 64, 32, 10_000, 4_000_000, 640, 2, 4096);
        try (var store = store(tenObjects)) {
            var writer = store.begin(header);
            for (int tick = 0; tick < 15; tick++) {
                int frame = tick;
                assertTrue(writer.offer(() -> new ReplayEvent.Frame(frame, List.of())));
            }
            var saved = writer.finish(15, "completed").get(5, TimeUnit.SECONDS);
            assertTrue(writer.full());
            assertEquals(10, saved.durationTicks());
            assertEquals("OBJECT_LIMIT", saved.endReason());
            var loaded = store.load(header.id());
            assertEquals(10, loaded.events().size());
            assertEquals("OBJECT_LIMIT", loaded.endReason());
        }
    }

    @Test
    public void boundsQueueAndAbortsWithoutPublishing() throws Exception {
        var header = header();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var store = store(LIMITS)) {
            var writer = store.begin(header);
            assertTrue(writer.offer(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return new ReplayEvent.Frame(0, List.of());
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertTrue(writer.offer(() -> new ReplayEvent.Frame(1, List.of())));
                assertTrue(writer.offer(() -> new ReplayEvent.Frame(2, List.of())));
                assertFalse(writer.offer(() -> new ReplayEvent.Frame(3, List.of())));
                assertThrows(IOException.class, () -> store.begin(header()));
                writer.abort();
            } finally {
                release.countDown();
            }
            assertThrows(ExecutionException.class, () -> writer.finish(4, "aborted").get(5, TimeUnit.SECONDS));
            assertFalse(Files.exists(store.path(header.id())));
        }
    }

    @Test
    public void rejectsInvalidPaletteAndAdmissionDiskBudget() throws Exception {
        var header = header();
        try (var store = store(LIMITS)) {
            var writer = store.begin(header);
            int[] blocks = new int[4096];
            blocks[0] = 1;
            assertTrue(writer.offer(() -> new ReplayEvent.Chunk(0, UUID.randomUUID(), 0, 0, 0, 16,
                    List.of("minecraft:air"), blocks)));
            assertThrows(ExecutionException.class, () -> writer.finish(1, "completed").get(5, TimeUnit.SECONDS));
            assertFalse(Files.exists(store.path(header.id())));
        }
        try (var store = new ReplayStore(temporary.getRoot().toPath(), LIMITS, 1, 0)) {
            assertThrows(ExecutionException.class,
                    () -> store.begin(header).finish(0, "completed").get(5, TimeUnit.SECONDS));
            assertFalse(Files.exists(store.path(header.id())));
        }
        for (boolean compressed : List.of(true, false)) {
            var small = new ReplayLimits(2, 1200, 4, 16, 8, 1, 1, 64, 2, 10_000,
                    compressed ? 16 : 4_000_000, compressed ? 8_000_000 : 16, 2, 4096);
            try (var store = store(small)) {
                assertThrows(ExecutionException.class,
                        () -> store.begin(header).finish(0, "completed").get(5, TimeUnit.SECONDS));
                assertFalse(Files.exists(store.path(header.id())));
            }
        }
    }

    @Test
    public void allowsImmediateNextRoundButBoundsUnfinishedSaves() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var store = store(LIMITS)) {
            var first = store.begin(header());
            assertTrue(first.offer(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return new ReplayEvent.Frame(0, List.of());
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                first.finish(1, "completed");
                var second = store.begin(header());
                var secondSave = second.finish(0, "completed");
                assertThrows(IOException.class, () -> store.begin(header()));
                release.countDown();
                first.finish(1, "completed").get(5, TimeUnit.SECONDS);
                secondSave.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private ReplayStore store(ReplayLimits limits) {
        return new ReplayStore(temporary.getRoot().toPath(), limits, 32_000_000, 0);
    }

    @Test
    public void requiresExplicitReadinessAndCountsInterruptedRecordingFiles() throws Exception {
        var header = header();
        try (var store = store(LIMITS)) {
            assertThrows(IOException.class, () -> store.markReady(header.id()));
            store.begin(header).finish(1, "DURATION_LIMIT").get(5, TimeUnit.SECONDS);
            assertFalse(store.isReady(header.id()));
            store.markReady(header.id());
            store.markReady(header.id());
            assertTrue(store.isReady(header.id()));
        }
        var partial = temporary.getRoot().toPath().resolve(UUID.randomUUID() + ".part");
        Files.write(partial, new byte[4096]);
        try (var store = new ReplayStore(temporary.getRoot().toPath(), LIMITS, LIMITS.maxArchiveBytes() + 4095, 0)) {
            assertThrows(ExecutionException.class,
                    () -> store.begin(header()).finish(0, "completed").get(5, TimeUnit.SECONDS));
            assertTrue(Files.isRegularFile(partial));
        }
    }

    private static ReplayHeader header() {
        return new ReplayHeader(UUID.randomUUID(), "rush", "test", "26.3", "test", "arena",
                Instant.ofEpochMilli(1234), List.of(new ReplayHeader.Participant(UUID.randomUUID(), "Thomas", "", "")));
    }

    @Test
    public void drainsReadinessBeforeShutdownAndReportsLateSubmission() throws Exception {
        var header = header();
        var store = store(LIMITS);
        store.begin(header).finish(1, "MATCH_END").get(5, TimeUnit.SECONDS);
        var ready = store.markReadyAsync(header.id());
        store.close();
        ready.get(5, TimeUnit.SECONDS);
        assertTrue(store.isReady(header.id()));
        assertThrows(ExecutionException.class, () -> store.markReadyAsync(header.id()).get(5, TimeUnit.SECONDS));
    }

    @Test
    public void marksReadinessInlineInWriterCompletionBeforeNextRecording() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var store = store(LIMITS)) {
            var header = header();
            var writer = store.begin(header);
            assertTrue(writer.offer(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                return new ReplayEvent.Frame(0, List.of());
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var ready = writer.finish(1, "MATCH_END").thenCompose(saved -> {
                    var marked = store.markReadyAsync(saved.header().id());
                    assertTrue("Writer completion must mark inline", marked.isDone());
                    return marked;
                });
                var next = store.begin(header());
                release.countDown();
                ready.get(5, TimeUnit.SECONDS);
                assertTrue(store.isReady(header.id()));
                next.finish(0, "MATCH_END").get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }
}
