package me.blockcraft.core.listener;

import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;

/** The {@code chat} section of config.yml; the filter's lists are in {@code chat-filter.yml}. */
public record ChatConfig(Duration cooldown, Duration networkRefresh) {

    public static ChatConfig from(StrictConfig chat) {
        return new ChatConfig(Duration.ofSeconds(chat.integer("cooldown-seconds")),
                Duration.ofSeconds(chat.integer("network-refresh-seconds")));
    }
}
