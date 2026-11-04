package me.blockcraft.lobby.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lobby's {@code flights} section: every {@code interval-seconds} one of {@code models}, props pieces built with their
 * front along +z, crosses the sky {@code height} blocks above the spawn, {@code length} blocks from end to end in
 * {@code flight-ticks}.
 */
public record FlightsConfig(boolean enabled, Duration interval, int height, int length, int flightTicks, List<String> models) {

    public static FlightsConfig fromYaml(Map<String, Object> config) {
        var models = config.get("models") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.<String>of();
        return new FlightsConfig(!Boolean.FALSE.equals(config.get("enabled")),
                Duration.ofSeconds(number(config, "interval-seconds", 300, 30, 86400)),
                number(config, "height", 85, 8, 200), number(config, "length", 180, 32, 400),
                number(config, "flight-ticks", 900, 100, 6000), models);
    }

    private static int number(Map<String, Object> config, String key, int fallback, int min, int max) {
        return Math.clamp(config.get(key) instanceof Number number ? number.intValue() : fallback, min, max);
    }
}
