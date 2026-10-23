package me.blockcraft.api.task;

import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Blocking work off the main thread that runs one job at a time per key, in the order it was submitted; different
 * keys still run side by side. Bukkit's async pool alone runs a save and the delete right behind it concurrently,
 * so the stale row can land last.
 */
public final class SerialTasks<K> {

    private final Plugin plugin;
    private final Map<K, CompletableFuture<?>> tails = new ConcurrentHashMap<>();

    public SerialTasks(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Completes off the main thread. A failed job is logged and completes exceptionally; the next one still runs. */
    public <T> CompletableFuture<T> submit(K key, Supplier<T> work) {
        var job = new CompletableFuture<T>();
        tails.compute(key, (ignored, tail) -> {
            if (tail == null) {
                start(key, job, work);
            } else {
                tail.whenComplete((result, failure) -> start(key, job, work));
            }
            return job;
        });
        job.whenComplete((result, failure) -> tails.remove(key, job));
        return job;
    }

    public void run(K key, Runnable work) {
        submit(key, () -> {
            work.run();
            return null;
        });
    }

    /** {@link Tasks#async(Plugin, Supplier, Consumer, Runnable)} in line with the key's other jobs. */
    public <T> void submit(K key, Supplier<T> work, Consumer<T> onMainThread, Runnable onFailure) {
        submit(key, work).whenComplete((result, failure) -> {
            if (plugin.isEnabled()) {
                Tasks.sync(plugin, failure == null ? () -> onMainThread.accept(result) : onFailure);
            }
        });
    }

    private <T> void start(K key, CompletableFuture<T> job, Supplier<T> work) {
        try {
            Tasks.async(plugin, () -> {
                try {
                    job.complete(work.get());
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Queued task for " + key + " failed", e);
                    job.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not queue a task for " + key, e);
            job.completeExceptionally(e);
        }
    }
}
