package me.blockcraft.core.listener;

import me.blockcraft.common.config.YamlFile;

import java.time.Duration;
import java.util.Map;

/** The {@code chat} section of config.yml; the filter's lists are in {@code chat-filter.yml}. */
public record ChatConfig(Duration cooldown, Duration networkRefresh) {

    public static ChatConfig fromYaml(Map<String, Object> root) {
        var chat = YamlFile.section(root, "chat");
        return new ChatConfig(
                Duration.ofSeconds(chat.get("cooldown-seconds") instanceof Number seconds ? seconds.longValue() : 3),
                Duration.ofSeconds(chat.get("network-refresh-seconds") instanceof Number seconds ? seconds.longValue() : 3));
    }
}
