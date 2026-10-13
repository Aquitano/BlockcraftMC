package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.HourWindow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Lobby's {@code shooting-stars} section; {@code night} is read in the network calendar's zone. */
public record ShootingStarConfig(boolean enabled, HourWindow night, Duration checkInterval, int odds, Duration cooldown,
                                 Duration wishWindow, int animationTicks, int height, int length, List<String> wishPrefixes) {

    public static ShootingStarConfig fromYaml(Map<String, Object> config) {
        var prefixes = config.get("wish-prefixes") instanceof List<?> values
                ? values.stream().map(String::valueOf).map(String::strip).filter(value -> !value.isEmpty()).toList() : List.<String>of();
        return new ShootingStarConfig(!Boolean.FALSE.equals(config.get("enabled")), HourWindow.fromYaml(config, 22, 2),
                Duration.ofSeconds(positive(config, "check-seconds", 60)), positive(config, "odds", 30),
                Duration.ofSeconds(positive(config, "cooldown-seconds", 1800)),
                Duration.ofSeconds(positive(config, "wish-seconds", 15)), Math.clamp(positive(config, "animation-ticks", 40), 1, 200),
                Math.clamp(positive(config, "height", 24), 1, 256), Math.clamp(positive(config, "length", 60), 1, 256),
                prefixes.isEmpty() ? List.of("ich wünsche mir", "ich wuensche mir", "i wish") : prefixes);
    }

    private static int positive(Map<String, Object> config, String key, int fallback) {
        return Math.clamp(config.get(key) instanceof Number number ? number.intValue() : fallback, 1, 86400);
    }
}
