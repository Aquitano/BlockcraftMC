package me.blockcraft.core.staff;

import me.blockcraft.common.anticheat.AnticheatEvidence;
import me.blockcraft.common.anticheat.AnticheatEvidenceRepository;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AnticheatHistory implements AutoCloseable {

    public record Settings(int days, int maximum, int pending) {
        public static Settings fromYaml(Map<String, Object> section) {
            return new Settings(number(section, "history-days", 7, 1, 30),
                    number(section, "history-max-entries", 10_000, 100, 100_000),
                    number(section, "history-pending-capacity", 256, 16, 4096));
        }

        private static int number(Map<String, Object> section, String key, int fallback, int minimum, int maximum) {
            int value = section.get(key) instanceof Number configured ? configured.intValue() : fallback;
            return Math.clamp(value, minimum, maximum);
        }
    }

    private final ThreadPoolExecutor worker;
    private final Consumer<AnticheatEvidence> append;
    private final Runnable prune;
    private final Logger logger;
    private final AtomicBoolean maintenancePending = new AtomicBoolean();
    private final AtomicLong lastWarning = new AtomicLong();
    private int sincePrune;
    private boolean retentionReady;

    public AnticheatHistory(AnticheatEvidenceRepository repository, Settings settings, Logger logger) {
        this(repository::append, () -> repository.prune(settings.days(), settings.maximum()), settings.pending(), logger);
    }

    AnticheatHistory(Consumer<AnticheatEvidence> append, Runnable prune, int capacity, Logger logger) {
        this.append = append;
        this.prune = prune;
        this.logger = logger;
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), work -> {
            var thread = new Thread(work, "blockcraft-anticheat-history");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean record(AnticheatEvidence evidence) {
        return submit(() -> {
            if (!retentionReady || sincePrune >= 64) {
                trim();
            }
            append.accept(evidence);
            sincePrune++;
        });
    }

    public void maintain() {
        if (!maintenancePending.compareAndSet(false, true)) {
            return;
        }
        if (!submit(() -> {
            try {
                trim();
            } finally {
                maintenancePending.set(false);
            }
        })) {
            maintenancePending.set(false);
        }
    }

    private void trim() {
        retentionReady = false;
        prune.run();
        sincePrune = 0;
        retentionReady = true;
    }

    private boolean submit(Runnable work) {
        try {
            worker.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException failure) {
                    warn("Anticheat history could not be stored or trimmed", failure);
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            if (!worker.isShutdown()) {
                warn("Anticheat history queue is full; some notices will not be stored", null);
            }
            return false;
        }
    }

    public void warn(String message, Throwable failure) {
        long now = System.currentTimeMillis();
        long previous = lastWarning.get();
        if (now - previous >= 60_000 && lastWarning.compareAndSet(previous, now)) {
            logger.log(Level.WARNING, message, failure);
        }
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(1, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
