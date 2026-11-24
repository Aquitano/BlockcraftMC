package me.blockcraft.starwars;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;

record WeaponSpec(String id, double damage, int pellets, double spread, int cooldown, double range, double heat, Mode mode) {
    enum Mode { BOLT, BURST, ROCKET, GRENADE }

    static Map<String, WeaponSpec> load(ConfigurationSection section) {
        var result = new LinkedHashMap<String, WeaponSpec>();
        if (section == null) return Map.of();
        for (var id : section.getKeys(false)) {
            var values = section.getConfigurationSection(id);
            if (values == null) continue;
            var mode = switch (values.getString("mode", "bolt")) {
                case "burst" -> Mode.BURST;
                case "rocket" -> Mode.ROCKET;
                case "grenade" -> Mode.GRENADE;
                default -> Mode.BOLT;
            };
            result.put(id, new WeaponSpec(id, Math.clamp(values.getDouble("damage"), 0, 100),
                    Math.clamp(values.getInt("pellets"), 1, 16), Math.clamp(values.getDouble("spread"), 0, .5),
                    Math.clamp(values.getInt("cooldown"), 2, 200), Math.clamp(values.getDouble("range"), 1, 128),
                    Math.clamp(values.getDouble("heat"), 1, 100), mode));
        }
        return Map.copyOf(result);
    }
}
