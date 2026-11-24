package me.blockcraft.api.task;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.IntConsumer;

/**
 * Ticks once per second: {@code onTick} receives the remaining seconds after each tick ({@code seconds - 1} down
 * to {@code 0}) and {@code onFinish} runs right after the tick that reaches zero. {@link #start} replaces a running
 * countdown, so one instance can drive consecutive phases.
 */
public final class Countdown {

    private static final long SECOND_TICKS = 20L;

    private final Plugin plugin;
    private BukkitTask task;
    private int remaining;

    public Countdown(Plugin plugin) {
        this.plugin = plugin;
    }

    public static Countdown start(Plugin plugin, int seconds, IntConsumer onTick, Runnable onFinish) {
        var countdown = new Countdown(plugin);
        countdown.start(seconds, onTick, onFinish);
        return countdown;
    }

    public void start(int seconds, IntConsumer onTick, Runnable onFinish) {
        cancel();
        remaining = seconds;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            remaining--;
            onTick.accept(remaining);
            if (remaining <= 0) {
                cancel();
                onFinish.run();
            }
        }, SECOND_TICKS, SECOND_TICKS);
    }

    public int remaining() {
        return remaining;
    }

    public void remaining(int seconds) {
        remaining = seconds;
    }

    public boolean isRunning() {
        return task != null;
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
