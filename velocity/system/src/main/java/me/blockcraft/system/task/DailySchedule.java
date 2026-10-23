package me.blockcraft.system.task;

import com.velocitypowered.api.scheduler.Scheduler;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Runs a task once a day at a fixed hour of the zone's wall clock, starting with the next occurrence. Each run
 * schedules the next one, because a day is not always 24 hours long where clocks change.
 */
public final class DailySchedule {

    private DailySchedule() {
    }

    public static void at(int hour, ZoneId zone, Scheduler scheduler, Object plugin, Runnable task) {
        var now = ZonedDateTime.now(zone);
        var next = now.with(LocalTime.of(hour, 0));
        schedule(next.isAfter(now) ? next : next.plusDays(1), scheduler, plugin, task);
    }

    private static void schedule(ZonedDateTime run, Scheduler scheduler, Object plugin, Runnable task) {
        scheduler.buildTask(plugin, () -> {
                    try {
                        task.run();
                    } finally {
                        schedule(run.plusDays(1), scheduler, plugin, task);
                    }
                })
                .delay(Duration.between(ZonedDateTime.now(run.getZone()), run))
                .schedule();
    }
}
