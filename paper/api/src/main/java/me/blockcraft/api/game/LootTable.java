package me.blockcraft.api.game;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/** Weighted chest loot as configured under a {@code min-items} / {@code max-items} / {@code loot} section. */
public record LootTable(int minItems, int maxItems, List<Entry> entries) {

    public record Entry(Material material, int amount, int weight) {
    }

    public static LootTable fromYaml(ConfigurationSection chest, int defaultMinItems, int defaultMaxItems) {
        var entries = chest.getMapList("loot").stream()
                .map(entry -> new Entry(
                        Material.valueOf(String.valueOf(entry.get("item"))),
                        number(entry, "amount", 1),
                        number(entry, "weight", 1)))
                .toList();
        return new LootTable(chest.getInt("min-items", defaultMinItems), chest.getInt("max-items", defaultMaxItems), entries);
    }

    private static int number(Map<?, ?> entry, String key, int fallback) {
        return entry.get(key) instanceof Number number ? number.intValue() : fallback;
    }
}
