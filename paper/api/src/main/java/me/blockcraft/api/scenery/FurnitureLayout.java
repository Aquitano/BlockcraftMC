package me.blockcraft.api.scenery;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record FurnitureLayout(List<Placement> placements) {

    public record Placement(String id, String item, double x, double y, double z, float yaw,
                            Set<Occasion> during, Set<String> groups, Map<Occasion, String> variants,
                            Optional<String> fallbackBlock, int fallbackHeight) {
        public Optional<String> item(Set<Occasion> active, String group) {
            if (!groups.isEmpty() && !groups.contains(group) || !during.isEmpty() && during.stream().noneMatch(active::contains)) {
                return Optional.empty();
            }
            return Optional.of(variants.entrySet().stream().filter(entry -> active.contains(entry.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse(item));
        }
    }

    public static FurnitureLayout load(Path file) {
        return parse(YamlConfiguration.loadConfiguration(file.toFile()));
    }

    static FurnitureLayout parse(ConfigurationSection config) {
        var section = config.getConfigurationSection("placements");
        var result = new ArrayList<Placement>();
        if (section == null) {
            return new FurnitureLayout(List.of());
        }
        for (var id : section.getKeys(false)) {
            var entry = section.getConfigurationSection(id);
            if (entry == null) {
                throw new IllegalArgumentException("Invalid furniture placement: " + id);
            }
            var variants = new LinkedHashMap<Occasion, String>();
            var choices = entry.getConfigurationSection("variants");
            if (choices != null) {
                for (var occasion : choices.getKeys(false)) {
                    variants.put(occasion(occasion), itemId(choices.getString(occasion)));
                }
            }
            var during = entry.getStringList("during").stream().map(FurnitureLayout::occasion).collect(Collectors.toUnmodifiableSet());
            result.add(new Placement(id, itemId(entry.getString("item")), coordinate(entry, "x"), coordinate(entry, "y"),
                    coordinate(entry, "z"), (float) entry.getDouble("yaw", 0), during, Set.copyOf(entry.getStringList("groups")),
                    java.util.Collections.unmodifiableMap(variants), Optional.ofNullable(entry.getString("fallback-block")),
                    Math.clamp(entry.getInt("fallback-height", 1), 1, 3)));
        }
        return new FurnitureLayout(List.copyOf(result));
    }

    private static double coordinate(ConfigurationSection entry, String axis) {
        Object raw = entry.get("at." + axis);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Furniture needs a finite at." + axis + ": " + entry.getCurrentPath());
        }
        return number.doubleValue();
    }

    private static String itemId(String value) {
        if (value == null || !value.matches("blockcraft_[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid Blockcraft furniture item: " + value);
        }
        return value;
    }

    private static Occasion occasion(String value) {
        return Occasion.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
