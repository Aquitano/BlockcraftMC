package me.blockcraft.system.command.staff;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.blockcraft.common.message.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Announces {@code <key>.countdown-60} to everyone, then -30, -10 and -5, and runs the end a minute later. */
final class MinuteCountdown {

    private MinuteCountdown() {
    }

    static List<ScheduledTask> start(Object plugin, ProxyServer proxy, Messages messages, String key, Runnable end) {
        proxy.sendMessage(messages.get(key + ".countdown-60"));
        var tasks = new ArrayList<ScheduledTask>();
        for (int left : List.of(30, 10, 5)) {
            tasks.add(proxy.getScheduler().buildTask(plugin, () -> proxy.sendMessage(messages.get(key + ".countdown-" + left)))
                    .delay(60 - left, TimeUnit.SECONDS).schedule());
        }
        tasks.add(proxy.getScheduler().buildTask(plugin, end).delay(60, TimeUnit.SECONDS).schedule());
        return tasks;
    }
}
