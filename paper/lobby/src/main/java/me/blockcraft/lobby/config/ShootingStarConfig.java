package me.blockcraft.lobby.config;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record ShootingStarConfig(boolean enabled, ZoneId zone, int fromHour, int untilHour, Duration checkInterval,
                                 int odds, Duration cooldown, Duration wishWindow, int animationTicks,
                                 int height, int length, List<String> wishPrefixes) {

    public static ShootingStarConfig fromYaml(Map<String, Object> config) {
        var prefixes = config.get("wish-prefixes") instanceof List<?> values
                ? values.stream().map(String::valueOf).map(String::strip).filter(value -> !value.isEmpty()).toList() : List.<String>of();
        return new ShootingStarConfig(!Boolean.FALSE.equals(config.get("enabled")),
                ZoneId.of(String.valueOf(config.getOrDefault("zone", "Europe/Berlin"))),
                Math.clamp(number(config, "from-hour", 22), 0, 23), Math.clamp(number(config, "until-hour", 2), 0, 24),
                Duration.ofSeconds(positive(config, "check-seconds", 60)), positive(config, "odds", 30),
                Duration.ofSeconds(positive(config, "cooldown-seconds", 1800)),
                Duration.ofSeconds(positive(config, "wish-seconds", 15)), Math.clamp(positive(config, "animation-ticks", 40), 1, 200),
                Math.clamp(positive(config, "height", 24), 1, 256), Math.clamp(positive(config, "length", 60), 1, 256),
                prefixes.isEmpty() ? List.of("ich wünsche mir", "ich wuensche mir", "i wish") : prefixes);
    }

    public boolean nightAt(Instant now) {
        int hour = now.atZone(zone).getHour();
        return fromHour <= untilHour ? hour >= fromHour && hour < untilHour : hour >= fromHour || hour < untilHour;
    }

    private static int number(Map<String, Object> config, String key, int fallback) {
        return config.get(key) instanceof Number number ? number.intValue() : fallback;
    }

    private static int positive(Map<String, Object> config, String key, int fallback) {
        return Math.clamp(number(config, key, fallback), 1, 86400);
    }
}
