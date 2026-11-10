package me.blockcraft.system.network;

import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.system.SystemConfig;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Tells the Discord webhook when a server keeps lagging and when it runs smoothly again. Changes the rate limit
 * swallows are sent together with the next alert that gets through.
 */
public final class LagAlert {

    private final SystemConfig config;
    private final Messages messages;
    private final AlertWebhook webhook;
    private final LagWatch watch;

    public LagAlert(SystemConfig config, Messages messages, Logger logger) {
        this.config = config;
        this.messages = messages;
        this.webhook = new AlertWebhook(config.alerts(), logger, Instant.EPOCH);
        this.watch = new LagWatch(config.alerts().lagMspt(), config.alerts().lagPolls());
    }

    void check(List<ServerRecord> alive) {
        var changes = watch.poll(alive);
        if (changes.isEmpty()) {
            return;
        }
        var text = changes.stream().map(this::text).collect(Collectors.joining("\n"));
        if (webhook.send(text)) {
            watch.announced(changes);
        }
    }

    private String text(LagWatch.Change change) {
        var status = change.server().status();
        return messages.raw(change.lagging() ? "alerts.lag" : "alerts.lag-over")
                .replace("<ip>", config.network().ip())
                .replace("<server>", change.server().name())
                .replace("<mspt>", String.format(Locale.GERMANY, "%.1f", status.mspt()))
                .replace("<tps>", String.format(Locale.GERMANY, "%.1f", status.tps()));
    }
}
