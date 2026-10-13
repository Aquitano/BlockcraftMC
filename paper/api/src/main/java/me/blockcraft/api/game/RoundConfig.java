package me.blockcraft.api.game;

import me.blockcraft.common.config.YamlFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * What BlockcraftAPI's config.yml sets for the round of every game: the lead that makes an opponent a nemesis
 * ({@code nemesis}), how long a death collects respect ({@code respect}), and the words that call a killer a
 * cheater within how long of the kill ({@code hacker}).
 */
public record RoundConfig(int nemesisLead, Duration respectWindow, Duration accusationWindow, List<String> accusations,
                          Duration creditWindow) {

    public static RoundConfig from(Map<String, Object> config) {
        var hacker = YamlFile.section(config, "hacker");
        return new RoundConfig(
                number(YamlFile.section(config, "nemesis"), "lead", 3),
                Duration.ofSeconds(number(YamlFile.section(config, "respect"), "seconds", 10)),
                Duration.ofSeconds(number(hacker, "window-seconds", 60)),
                hacker.get("words") instanceof List<?> words ? words.stream().map(String::valueOf).toList() : List.of(),
                Duration.ofSeconds(Math.max(0, number(YamlFile.section(config, "death-lines"), "credit-seconds", 10))));
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number value ? value.intValue() : fallback;
    }
}
