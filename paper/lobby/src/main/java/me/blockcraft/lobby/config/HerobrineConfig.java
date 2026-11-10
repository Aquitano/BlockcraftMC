package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.HourWindow;

import java.time.Duration;
import java.util.Map;

/** Lobby's {@code herobrine} section; {@code night} is read in the network calendar's zone. */
public record HerobrineConfig(boolean enabled, HourWindow night, Duration checkInterval, int odds, Duration cooldown,
                              int distance, int vanishDistance, Duration lifetime) {

    public static HerobrineConfig fromYaml(Map<String, Object> config) {
        return new HerobrineConfig(!Boolean.FALSE.equals(config.get("enabled")), HourWindow.fromYaml(config, 22, 4),
                Duration.ofSeconds(number(config, "check-seconds", 60, 1, 3600)), number(config, "odds", 120, 1, 100000),
                Duration.ofSeconds(number(config, "cooldown-seconds", 3600, 1, 86400)),
                number(config, "distance", 32, 8, 96), number(config, "vanish-distance", 16, 2, 64),
                Duration.ofSeconds(number(config, "seconds", 30, 5, 600)));
    }

    private static int number(Map<String, Object> config, String key, int fallback, int min, int max) {
        return Math.clamp(config.get(key) instanceof Number number ? number.intValue() : fallback, min, max);
    }
}
