package me.blockcraft.api.task;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class Tasks {

    private Tasks() {
    }

    public static void async(Plugin plugin, Runnable work) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
    }

    /** Runs blocking work off the main thread and hands its result back on it; a failure is only logged. */
    public static <T> void async(Plugin plugin, Supplier<T> work, Consumer<T> onMainThread) {
        async(plugin, work, onMainThread, () -> { });
    }

    /** As {@link #async(Plugin, Supplier, Consumer)}, but a failed {@code work} runs {@code onFailure} on the main thread. */
    public static <T> void async(Plugin plugin, Supplier<T> work, Consumer<T> onMainThread, Runnable onFailure) {
        async(plugin, "Background task failed", work, onMainThread, onFailure);
    }

    static <T> void async(Plugin plugin, String failureContext, Supplier<T> work, Consumer<T> onMainThread, Runnable onFailure) {
        async(plugin, () -> {
            Runnable completion;
            try {
                T result = work.get();
                completion = () -> onMainThread.accept(result);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, failureContext, e);
                completion = onFailure;
            }
            sync(plugin, completion);
        });
    }

    public static void sync(Plugin plugin, Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()) {
                    task.run();
                }
            });
        }
    }

    public static void later(Plugin plugin, long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }
}
