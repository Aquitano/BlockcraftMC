package me.blockcraft.lobby.config;

import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;
import java.util.List;

/**
 * Lobby's {@code flights} section: every {@code interval-seconds} one of {@code models}, props pieces built with their
 * front along +z, crosses the sky {@code height} blocks above the spawn, {@code length} blocks from end to end in
 * {@code flight-ticks}.
 */
public record FlightsConfig(boolean enabled, Duration interval, int height, int length, int flightTicks, List<String> models) {

    public static FlightsConfig from(StrictConfig config) {
        return new FlightsConfig(config.flag("enabled"),
                Duration.ofSeconds(Math.clamp(config.integer("interval-seconds"), 30, 86400)),
                Math.clamp(config.integer("height"), 8, 200), Math.clamp(config.integer("length"), 32, 400),
                Math.clamp(config.integer("flight-ticks"), 100, 6000), config.texts("models"));
    }
}
