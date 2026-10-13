package me.blockcraft.lobby.activity;

import me.blockcraft.lobby.config.ShootingStarConfig;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShootingStarConfigTest {

    @Test
    public void berlinNightStartsAtTenAndEndsBeforeTwoAcrossMidnight() {
        var config = ShootingStarConfig.fromYaml(Map.of());

        assertFalse(config.nightAt(Instant.parse("2026-10-13T19:59:59Z")));
        assertTrue(config.nightAt(Instant.parse("2026-10-13T20:00:00Z")));
        assertTrue(config.nightAt(Instant.parse("2026-10-13T23:00:00Z")));
        assertFalse(config.nightAt(Instant.parse("2026-10-14T00:00:00Z")));
    }
}
