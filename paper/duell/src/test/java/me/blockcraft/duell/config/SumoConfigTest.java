package me.blockcraft.duell.config;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SumoConfigTest {

    @Test
    public void readsTheBundledSumoSectionAndOverridesOnTop() {
        var config = bundled();
        assertEquals(new SumoConfig(4, 64, 61, 2, 3, 3), sumo(config));

        config.createSection("sumo", Map.of("radius", 6, "floor-y", 80, "lose-y", 75,
                "spawn-distance", 3.5, "points-to-win", 5, "reset-seconds", 4));

        assertEquals(new SumoConfig(6, 80, 75, 3.5, 5, 4), sumo(config));
    }

    @Test
    public void rejectsUnsafeGeometryAndNonfiniteOrUnboundedGameplaySettings() {
        var invalid = Map.<String, Object[]>of("radius", new Object[] {1, 17},
                "floor-y", new Object[] {-1, 201}, "lose-y", new Object[] {-61, 64, 65},
                "spawn-distance", new Object[] {0.5, 3.1, Double.NaN, Double.POSITIVE_INFINITY},
                "points-to-win", new Object[] {0, 101}, "reset-seconds", new Object[] {0, 31});

        invalid.forEach((key, values) -> {
            for (var value : values) {
                var config = bundled();
                config.set("sumo." + key, value);
                assertThrows(key + "=" + value, RuntimeException.class, () -> sumo(config));
            }
        });
    }

    private static SumoConfig sumo(ConfigurationSection config) {
        return SumoConfig.from(BundledDefaults.strict(config).section("sumo"));
    }

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                SumoConfigTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }
}
