package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.HourWindow;
import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;

/** Lobby's {@code herobrine} section; {@code night} is read in the network calendar's zone. */
public record HerobrineConfig(boolean enabled, HourWindow night, Duration checkInterval, int odds, Duration cooldown,
                              int distance, int vanishDistance, Duration lifetime) {

    public static HerobrineConfig from(StrictConfig config) {
        return new HerobrineConfig(config.flag("enabled"), HourWindow.from(config),
                Duration.ofSeconds(Math.clamp(config.integer("check-seconds"), 1, 3600)),
                Math.clamp(config.integer("odds"), 1, 100000),
                Duration.ofSeconds(Math.clamp(config.integer("cooldown-seconds"), 1, 86400)),
                Math.clamp(config.integer("distance"), 8, 96), Math.clamp(config.integer("vanish-distance"), 2, 64),
                Duration.ofSeconds(Math.clamp(config.integer("seconds"), 5, 600)));
    }
}
