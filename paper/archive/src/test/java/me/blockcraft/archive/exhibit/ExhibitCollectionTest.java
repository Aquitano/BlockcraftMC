package me.blockcraft.archive.exhibit;

import org.bukkit.Material;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExhibitCollectionTest {

    @Test
    public void originsStaySeparateInConfiguredOrderAndUnknownOriginsRemainVisible() {
        var collections = ExhibitCollection.of(List.of(
                exhibit("survival", "AQ Survival", "Survival", 2020),
                exhibit("winter", "ClayMC", "Lobbys", 2019),
                exhibit("unknown", "Other", "Lobbys", 0),
                exhibit("lobby", "Blockcraft", "Lobbys", 0),
                exhibit("summer", "ClayMC", "Lobbys", 2019),
                exhibit("new", "ClayMC", "Lobbys", 2020)), List.of("Blockcraft", "ClayMC", "AQ Survival"));

        assertEquals(List.of("Blockcraft", "ClayMC", "AQ Survival", "Other"),
                collections.stream().map(ExhibitCollection::origin).toList());
        assertEquals(List.of("summer", "winter", "new"), collections.get(1).exhibits().stream().map(Exhibit::key).toList());
    }

    @Test
    public void smallCollectionsOpenDirectlyAndLargeMixedCollectionsUseCategories() {
        var mixed = IntStream.range(0, 10)
                .mapToObj(index -> exhibit("map" + index, "Blockcraft", index == 0 ? "Lobbys" : "BedWars", 0)).toList();

        assertFalse(new ExhibitCollection("Blockcraft", mixed.subList(0, 9)).hasCategories("Sonstiges"));
        assertTrue(new ExhibitCollection("Blockcraft", mixed).hasCategories("Sonstiges"));
        assertFalse(new ExhibitCollection("Blockcraft", IntStream.range(0, 10)
                .mapToObj(index -> exhibit("map" + index, "Blockcraft", "Lobbys", 0)).toList()).hasCategories("Sonstiges"));
        assertFalse(new ExhibitCollection("Blockcraft", IntStream.range(0, 10)
                .mapToObj(index -> exhibit("map" + index, "Blockcraft", index == 0 ? null : "Sonstiges", 0)).toList())
                .hasCategories("Sonstiges"));
    }

    private static Exhibit exhibit(String key, String origin, String category, int year) {
        return new Exhibit(key, key, origin, year == 0 ? OptionalInt.empty() : OptionalInt.of(year), Optional.ofNullable(category),
                "", Material.PAPER, Optional.empty(), List.of(), List.of(), List.of());
    }
}
