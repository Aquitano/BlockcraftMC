package me.blockcraft.duell.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DuellConfigTest {

    @Test
    public void tourIntermissionDefaultsToThreeSecondsAndReadsItsConfiguredValue() {
        var config = bundled();
        assertEquals(Duration.ofSeconds(3), DuellConfig.from(config).tourIntermission());

        config.set("tour.intermission-seconds", 5);
        assertEquals(Duration.ofSeconds(5), DuellConfig.from(config).tourIntermission());
    }

    @Test
    public void ratedPlayStaysDisabledUnlessExplicitlyEnabled() {
        var config = bundled();
        assertFalse(DuellConfig.from(config).ratedEnabled());

        config.set("rating.enabled", true);
        assertTrue(DuellConfig.from(config).ratedEnabled());
    }

    @Test
    public void tourIntermissionMustBeBetweenOneAndThirtySeconds() {
        for (int seconds : new int[] {-1, 0, 31}) {
            var config = bundled();
            config.set("tour.intermission-seconds", seconds);
            assertThrows(IllegalArgumentException.class, () -> DuellConfig.from(config));
        }
    }

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                DuellConfigTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }
}
