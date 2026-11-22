package me.blockcraft.lobby.config;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/** Lobby's {@code sleigh} section: when Santa flies over the lobby and what falls out of his sleigh. */
public record SleighConfig(boolean enabled, Set<Occasion> during, Duration interval, int height, int length, int flightTicks,
                           int presents, int presentCoins, Duration presentLifetime) {

    public static SleighConfig from(StrictConfig config) {
        var occasions = config.texts("during");
        var during = EnumSet.noneOf(Occasion.class);
        for (var occasion : Occasion.values()) {
            if (occasions.stream().anyMatch(name -> name.equalsIgnoreCase(occasion.name()))) {
                during.add(occasion);
            }
        }
        return new SleighConfig(config.flag("enabled"), Set.copyOf(during),
                Duration.ofSeconds(Math.clamp(config.integer("interval-seconds"), 60, 86400)),
                Math.clamp(config.integer("height"), 4, 128), Math.clamp(config.integer("length"), 16, 256),
                Math.clamp(config.integer("flight-ticks"), 40, 1200), Math.clamp(config.integer("presents"), 0, 20),
                Math.clamp(config.integer("present-coins"), 0, 10000),
                Duration.ofSeconds(Math.clamp(config.integer("present-seconds"), 5, 600)));
    }
}
