package me.blockcraft.duell.config;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SumoConfigTest {

    @Test
    public void readsDefaultsAndOverridesFromTheSumoSection() {
        var config = new MemoryConfiguration();
        assertEquals(new SumoConfig(4, 64, 61, 2, 3, 3, 0.4, 8), SumoConfig.from(config));

        config.createSection("sumo", Map.of("radius", 6, "floor-y", 80, "lose-y", 75,
                "spawn-distance", 3.5, "points-to-win", 5, "reset-seconds", 4,
                "knockback-strength", 0.6, "hit-delay-ticks", 10));

        assertEquals(new SumoConfig(6, 80, 75, 3.5, 5, 4, 0.6, 10), SumoConfig.from(config));
    }

    @Test
    public void rejectsUnsafeGeometryAndNonfiniteOrUnboundedGameplaySettings() {
        var invalid = Map.<String, Object[]>of("radius", new Object[] {1, 17},
                "floor-y", new Object[] {-1, 201}, "lose-y", new Object[] {-61, 64, 65},
                "spawn-distance", new Object[] {0.5, 3.1, Double.NaN, Double.POSITIVE_INFINITY},
                "points-to-win", new Object[] {0, 101}, "reset-seconds", new Object[] {0, 31},
                "knockback-strength", new Object[] {0, -0.4, 2.1, Double.NaN, Double.POSITIVE_INFINITY},
                "hit-delay-ticks", new Object[] {0, 21});

        invalid.forEach((key, values) -> {
            for (var value : values) {
                var config = new MemoryConfiguration();
                config.set("sumo." + key, value);
                assertThrows(key + "=" + value, IllegalArgumentException.class, () -> SumoConfig.from(config));
            }
        });
    }
}
