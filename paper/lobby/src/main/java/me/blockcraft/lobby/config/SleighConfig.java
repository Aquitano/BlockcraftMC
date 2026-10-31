package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lobby's {@code sleigh} section: when Santa flies over the lobby and what falls out of his sleigh. */
public record SleighConfig(boolean enabled, Set<Occasion> during, Duration interval, int height, int length, int flightTicks,
                           int presents, int presentCoins, Duration presentLifetime) {

    public static SleighConfig fromYaml(Map<String, Object> config) {
        var during = EnumSet.noneOf(Occasion.class);
        if (config.get("during") instanceof List<?> occasions) {
            for (var occasion : Occasion.values()) {
                if (occasions.stream().map(String::valueOf).anyMatch(name -> name.equalsIgnoreCase(occasion.name()))) {
                    during.add(occasion);
                }
            }
        } else {
            during.add(Occasion.CHRISTMAS);
        }
        return new SleighConfig(!Boolean.FALSE.equals(config.get("enabled")), Set.copyOf(during),
                Duration.ofSeconds(number(config, "interval-seconds", 600, 60, 86400)),
                number(config, "height", 18, 4, 128), number(config, "length", 80, 16, 256),
                number(config, "flight-ticks", 240, 40, 1200), number(config, "presents", 4, 0, 20),
                number(config, "present-coins", 15, 0, 10000),
                Duration.ofSeconds(number(config, "present-seconds", 30, 5, 600)));
    }

    private static int number(Map<String, Object> config, String key, int fallback, int min, int max) {
        return Math.clamp(config.get(key) instanceof Number number ? number.intValue() : fallback, min, max);
    }
}
