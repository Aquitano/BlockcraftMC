package me.blockcraft.api.game.rush;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

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

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void createsAnEditableNativePoolWithoutOverwritingExistingChoices() throws Exception {
        var file = temporary.getRoot().toPath().resolve("rush/maps.yml");
        assertEquals(6, MapPool.load(file, BOUNDS).maps().size());
        assertTrue(java.nio.file.Files.isRegularFile(file));
        var customized = YamlConfiguration.loadConfiguration(file.toFile());
        customized.set("selection", "quartz");
        customized.set("maps.quartz.name", "Meine Brücke");
        customized.save(file.toFile());
        var contents = java.nio.file.Files.readString(file);
        assertEquals("Meine Brücke", MapPool.load(file, BOUNDS).choose(new Random()).name());
        assertEquals(contents, java.nio.file.Files.readString(file));
    }

    @Test
    public void shipsSixDifferentLayoutsAndCanPinAnEnabledMap() {
        var yaml = defaults();
        var pool = MapPool.from(yaml, BOUNDS);
        assertEquals(6, pool.maps().size());
        assertEquals(5, pool.maps().stream().map(map -> map.dimensions().bridgeLength()).distinct().count());
        assertEquals(3, pool.maps().stream().map(map -> map.dimensions().baseRadius()).distinct().count());
        assertEquals(3, pool.maps().stream().map(map -> map.palette().bridge()).distinct().count());
        assertNotEquals(pool.maps().getFirst().dimensions().bridgeWidth(), pool.maps().get(1).dimensions().bridgeWidth());

        yaml.set("selection", "quartz");
        assertEquals("quartz", MapPool.from(yaml, BOUNDS).choose(new Random(1)).key());
        assertEquals("quartz", MapPool.from(yaml, BOUNDS).choose(new Random(2)).key());
    }

    @Test
    public void oldPoolsGainMissingBundledMapsWithoutReplacingCustomizations() {
        var yaml = defaults();
        yaml.set("maps.tightrope", null);
        yaml.set("maps.divide", null);
        yaml.set("maps.stepping", null);
        yaml.set("maps.sandstone.name", "Mein Steg");
        yaml.set("maps.sandstone.bridge-length", 19);
        yaml.set("maps.courtyard.enabled", false);
        yaml.set("selection", "sandstone");

        var pool = MapPool.from(yaml, BOUNDS, defaults());

        assertEquals(5, pool.maps().size());
        assertTrue(pool.maps().stream().anyMatch(map -> map.key().equals("divide")));
        assertFalse(pool.maps().stream().anyMatch(map -> map.key().equals("courtyard")));
        assertEquals("Mein Steg", pool.choose(new Random()).name());
        assertEquals(19, pool.choose(new Random()).dimensions().bridgeLength());
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
        for (var key : yaml.getConfigurationSection("maps").getKeys(false)) {
            yaml.set("maps." + key + ".enabled", false);
        }
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

    @Test
    public void oldDefinitionsKeepFullBasesAndContinuousBridges() {
        var pool = MapPool.from(defaults(), BOUNDS);
        for (var map : pool.maps().subList(0, 3)) {
            assertEquals(map.dimensions().baseRadius() * 2 + 1, map.geometry().baseWidth());
            assertEquals(0, map.geometry().centerGap());
            assertEquals(0, map.geometry().segmentLength());
            assertEquals(new MapPool.ArenaMap(map.key(), map.name(), map.dimensions(), map.palette()), map);
        }
    }

    @Test
    public void rejectsInvalidOptionalGeometryWithMapContext() {
        for (var entry : new Object[][] {
                {"base-width", 0}, {"base-width", 2}, {"base-width", 9}, {"base-width", "one"},
                {"center-gap", -1}, {"center-gap", 25}, {"center-gap", 2},
                {"segment-length", 3}, {"segment-gap", 2}}) {
            var yaml = defaults();
            yaml.set("maps.sandstone." + entry[0], entry[1]);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> MapPool.from(yaml, BOUNDS))
                    .getMessage().contains("maps.sandstone"));
        }
    }

    private static YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(MapPoolTest.class.getResourceAsStream("/rush/maps.yml")), StandardCharsets.UTF_8));
    }
}
