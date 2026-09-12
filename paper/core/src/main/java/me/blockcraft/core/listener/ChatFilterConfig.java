package me.blockcraft.core.listener;

import me.blockcraft.common.config.YamlFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** The {@code chat} section of config.yml. */
public record ChatFilterConfig(String serverName, Duration cooldown, String allowedDomain,
                               List<String> blockedWords, List<String> advertisementTerms) {

    public static ChatFilterConfig fromYaml(Map<String, Object> root) {
        var chat = YamlFile.section(root, "chat");
        return new ChatFilterConfig(
                String.valueOf(root.getOrDefault("server-name", "unknown")),
                Duration.ofSeconds(chat.get("cooldown-seconds") instanceof Number seconds ? seconds.longValue() : 3),
                String.valueOf(chat.getOrDefault("allowed-domain", "")).toLowerCase(),
                lowercased(chat, "blocked-words"),
                lowercased(chat, "advertisement-terms"));
    }

    private static List<String> lowercased(Map<String, Object> section, String key) {
        return section.get(key) instanceof List<?> values
                ? values.stream().map(String::valueOf).map(String::toLowerCase).toList()
                : List.of();
    }
}
