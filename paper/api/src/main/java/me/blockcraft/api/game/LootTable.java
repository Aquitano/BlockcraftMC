package me.blockcraft.api.game;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/** Weighted chest loot as configured under a {@code min-items} / {@code max-items} / {@code loot} section. */
public record LootTable(int minItems, int maxItems, List<Entry> entries) {

    public record Entry(Material material, int amount, int weight) {
    }

    public static LootTable fromYaml(ConfigurationSection chest) {
        var counts = BundledDefaults.strict(chest);
        return new LootTable(counts.integer("min-items"), counts.integer("max-items"), entries(chest));
    }

    /** The {@code loot} list of {@code section}; an entry without {@code amount} or {@code weight} counts 1 of each. */
    public static List<Entry> entries(ConfigurationSection section) {
        return section.getMapList("loot").stream()
                .map(entry -> new Entry(
                        Material.valueOf(String.valueOf(entry.get("item"))),
                        number(entry, "amount", 1),
                        number(entry, "weight", 1)))
                .toList();
    }

    private static int number(Map<?, ?> entry, String key, int fallback) {
        return entry.get(key) instanceof Number number ? number.intValue() : fallback;
    }
}
