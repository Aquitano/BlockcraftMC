package me.blockcraft.story.state;

import me.blockcraft.story.debug.Trace;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The party's scheduled actions: each runs once after its delay unless a jump or the end of the round clears it
 * first. {@code /story skip} can bring the next one forward.
 */
public final class Timers {

    public record Pending(String label, long due) { }

    private final class Timer {
        final String key;
        final String label;
        final long due;
        final Runnable action;
        BukkitTask task;

        Timer(String key, String label, long due, Runnable action) {
            this.key = key;
            this.label = label;
            this.due = due;
            this.action = action;
        }

        void fire() {
            task.cancel();
            pending.remove(this);
            trace.event("timer", "fired " + label);
            action.run();
        }
    }

    private final Plugin plugin;
    private final Trace trace;
    private final List<Timer> pending = new ArrayList<>();

    public Timers(Plugin plugin, Trace trace) {
        this.plugin = plugin;
        this.trace = trace;
    }

    public void after(int ticks, String key, String label, Runnable action) {
        var timer = new Timer(key, label, Bukkit.getCurrentTick() + (long) ticks, action);
        timer.task = Bukkit.getScheduler().runTaskLater(plugin, timer::fire, Math.max(1, ticks));
        pending.add(timer);
        trace.event("timer", "in " + ticks + " ticks: " + label);
    }

    /** Whether a timer for {@code key} still waits. */
    public boolean waiting(String key) {
        return pending.stream().anyMatch(timer -> timer.key.equals(key));
    }

    public List<Pending> pending() {
        return pending.stream().sorted(Comparator.comparingLong(timer -> timer.due)).map(timer -> new Pending(timer.label, timer.due)).toList();
    }

    /** Runs the timer that is due first now; false when none is waiting. */
    public boolean fireNext() {
        var next = pending.stream().min(Comparator.comparingLong(timer -> timer.due));
        next.ifPresent(Timer::fire);
        return next.isPresent();
    }

    public void clear(String cause) {
        if (pending.isEmpty()) return;
        pending.forEach(timer -> timer.task.cancel());
        trace.event("timer", pending.size() + " cleared by " + cause);
        pending.clear();
    }
}
