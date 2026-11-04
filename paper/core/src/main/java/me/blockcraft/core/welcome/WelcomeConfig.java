package me.blockcraft.core.welcome;

import me.blockcraft.common.config.YamlFile;

import java.util.List;
import java.util.Map;

/** The {@code welcome} section of config.yml. */
public record WelcomeConfig(List<String> words, int reward, int dailyCap, int committeeThreshold) {

    public static WelcomeConfig fromYaml(Map<String, Object> root) {
        var welcome = YamlFile.section(root, "welcome");
        return new WelcomeConfig(
                welcome.get("words") instanceof List<?> words ? words.stream().map(String::valueOf).toList() : List.of(),
                number(welcome, "reward-coins", 10),
                number(welcome, "daily-cap", 5),
                number(welcome, "committee-threshold", 10));
    }

    private static int number(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number value ? value.intValue() : fallback;
    }
}
