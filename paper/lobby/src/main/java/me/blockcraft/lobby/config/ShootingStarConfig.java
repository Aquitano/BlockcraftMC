package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.HourWindow;
import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;
import java.util.List;

/** Lobby's {@code shooting-stars} section; {@code night} is read in the network calendar's zone. */
public record ShootingStarConfig(boolean enabled, HourWindow night, Duration checkInterval, int odds, Duration cooldown,
                                 Duration wishWindow, int animationTicks, int height, int length, List<String> wishPrefixes) {

    public static ShootingStarConfig from(StrictConfig config) {
        var prefixes = config.texts("wish-prefixes").stream().map(String::strip).filter(value -> !value.isEmpty()).toList();
        return new ShootingStarConfig(config.flag("enabled"), HourWindow.from(config),
                Duration.ofSeconds(positive(config, "check-seconds")), positive(config, "odds"),
                Duration.ofSeconds(positive(config, "cooldown-seconds")),
                Duration.ofSeconds(positive(config, "wish-seconds")), Math.clamp(positive(config, "animation-ticks"), 1, 200),
                Math.clamp(positive(config, "height"), 1, 256), Math.clamp(positive(config, "length"), 1, 256), prefixes);
    }

    private static int positive(StrictConfig config, String key) {
        return Math.clamp(config.integer(key), 1, 86400);
    }
}
