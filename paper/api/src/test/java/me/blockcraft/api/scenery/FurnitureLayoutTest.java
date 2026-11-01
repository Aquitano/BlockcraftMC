package me.blockcraft.api.scenery;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FurnitureLayoutTest {

    @Test
    public void seasonalVariantAndGroupRestrictionsChooseOneAppearance() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                placements:
                  seat:
                    item: blockcraft_timber_bench
                    at: {x: -195, y: 47, z: -307}
                    groups: [bedwars]
                    variants: {winter: blockcraft_winter_bench}
                  gifts:
                    item: blockcraft_presents
                    at: {x: -197, y: 47, z: -299}
                    during: [advent, christmas]
                """);
        var placements = FurnitureLayout.parse(yaml).placements();
        assertEquals("blockcraft_timber_bench", placements.getFirst().item(Set.of(), "bedwars").orElseThrow());
        assertEquals("blockcraft_winter_bench", placements.getFirst().item(Set.of(Occasion.WINTER), "bedwars").orElseThrow());
        assertTrue(placements.getFirst().item(Set.of(Occasion.WINTER), "ttt").isEmpty());
        assertTrue(placements.getLast().item(Set.of(Occasion.WINTER), "bedwars").isEmpty());
        assertEquals("blockcraft_presents", placements.getLast().item(Set.of(Occasion.ADVENT), "bedwars").orElseThrow());
    }

    @Test
    public void missingCoordinateCannotSilentlyPlaceFurnitureAtZero() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("placements: {seat: {item: blockcraft_timber_bench, at: {x: 2, z: 4}}}");
        assertThrows(IllegalArgumentException.class, () -> FurnitureLayout.parse(yaml));
    }
}
