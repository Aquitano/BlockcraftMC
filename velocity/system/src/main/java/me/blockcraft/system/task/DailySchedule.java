package me.blockcraft.system.task;

import com.velocitypowered.api.scheduler.Scheduler;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** Runs a task once a day at a fixed local hour, starting with the next occurrence. */
public final class DailySchedule {

    private DailySchedule() {
    }

    public static void at(int hour, Scheduler scheduler, Object plugin, Runnable task) {
        var now = ZonedDateTime.now();
        var next = now.with(LocalTime.of(hour, 0));
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        scheduler.buildTask(plugin, task)
                .delay(Duration.between(now, next))
                .repeat(Duration.ofDays(1))
                .schedule();
    }
}
