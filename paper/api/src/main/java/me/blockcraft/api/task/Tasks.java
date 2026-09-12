package me.blockcraft.api.task;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Tasks {

    private Tasks() {
    }

    public static void async(Plugin plugin, Runnable work) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
    }

    /** Runs blocking work off the main thread and hands its result back on it. */
    public static <T> void async(Plugin plugin, Supplier<T> work, Consumer<T> onMainThread) {
        async(plugin, () -> {
            T result = work.get();
            sync(plugin, () -> onMainThread.accept(result));
        });
    }

    public static void sync(Plugin plugin, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void later(Plugin plugin, long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }
}
