package me.blockcraft.mlgrush.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MapPoolTest {

    private static final RushConfig.ArenaSettings BOUNDS = new RushConfig.ArenaSettings(4, 16, 64, 48);

    @Test
    public void shipsThreeDifferentLayoutsAndCanPinAnEnabledMap() {
        var yaml = defaults();
        var pool = MapPool.from(yaml, BOUNDS);
        assertEquals(3, pool.maps().size());
        assertEquals(3, pool.maps().stream().map(map -> map.dimensions().bridgeLength()).distinct().count());
        assertEquals(3, pool.maps().stream().map(map -> map.dimensions().baseRadius()).distinct().count());
        assertEquals(3, pool.maps().stream().map(map -> map.palette().bridge()).distinct().count());
        assertNotEquals(pool.maps().getFirst().dimensions().bridgeWidth(), pool.maps().get(1).dimensions().bridgeWidth());

        yaml.set("selection", "quartz");
        assertEquals("quartz", MapPool.from(yaml, BOUNDS).choose(new Random(1)).key());
        assertEquals("quartz", MapPool.from(yaml, BOUNDS).choose(new Random(2)).key());
    }

    @Test
    public void randomSelectionUsesOnlyEnabledMapsAndCanBeReproduced() {
        var yaml = defaults();
        yaml.set("maps.courtyard.enabled", false);
        var pool = MapPool.from(yaml, BOUNDS);
        var first = new Random(7);
        var second = new Random(7);
        for (int i = 0; i < 20; i++) {
            var selected = pool.choose(first);
            assertFalse(selected.key().equals("courtyard"));
            assertTrue(pool.maps().contains(selected));
            assertEquals(selected, pool.choose(second));
        }
    }

    @Test
    public void rejectsEmptyPoolsUnknownSelectionsAndDisabledSelections() {
        var yaml = defaults();
        yaml.set("selection", "missing");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS))
                .getMessage().contains("selection"));
        yaml.set("selection", "courtyard");
        yaml.set("maps.courtyard.enabled", false);
        assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS));
        yaml.set("selection", "random");
        yaml.set("maps.sandstone.enabled", false);
        yaml.set("maps.quartz.enabled", false);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS))
                .getMessage().contains("enable at least one"));
    }

    @Test
    public void rejectsUnknownAndPhysicsDependentMaterialsWithTheMapAndField() {
        for (var material : new String[] {"SAND", "TNT", "OAK_STAIRS", "ICE", "AIR", "UNKNOWN"}) {
            var yaml = defaults();
            yaml.set("maps.sandstone.materials.bridge", material);
            var error = assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS));
            assertTrue(error.getMessage().contains("maps.sandstone"));
            assertTrue(error.getMessage().contains("materials.bridge"));
        }
    }

    @Test
    public void rejectsInvalidGeometryAndMalformedEntriesIncludingDisabledMaps() {
        for (var field : new String[] {"bridge-length", "bridge-width", "base-radius"}) {
            var yaml = defaults();
            yaml.set("maps.sandstone.enabled", false);
            yaml.set("maps.sandstone." + field, 0);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS))
                    .getMessage().contains("maps.sandstone"));
        }
        var yaml = defaults();
        yaml.set("maps.sandstone.bridge-width", 2);
        assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS));
        yaml.set("maps.sandstone.bridge-width", "three");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS))
                .getMessage().contains("bridge-width"));
        var invalid = defaults();
        invalid.set("maps.sandstone.enabled", "yes");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> MapPool.from(invalid, BOUNDS))
                .getMessage().contains("enabled"));
    }

    private static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(MapPoolTest.class.getResourceAsStream("/maps.yml")), StandardCharsets.UTF_8));
    }
}
