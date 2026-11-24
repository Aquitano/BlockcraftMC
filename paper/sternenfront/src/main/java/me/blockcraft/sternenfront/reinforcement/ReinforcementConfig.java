package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public record ReinforcementConfig(Map<Reinforcement, Stats> units) {

    public record Stats(int cost, int limit, double health, double speed) { }

    public static ReinforcementConfig from(ConfigurationSection config) {
        var section = BundledDefaults.strict(config).section("reinforcement-units");
        var units = new EnumMap<Reinforcement, Stats>(Reinforcement.class);
        for (var unit : Reinforcement.values()) {
            var stats = section.section(unit.key());
            units.put(unit, new Stats(Math.max(1, stats.integer("cost")), Math.max(0, stats.integer("limit")),
                    Math.clamp(stats.decimal("health"), 1, 100), Math.clamp(stats.decimal("speed"), 0.1, 2)));
        }
        return new ReinforcementConfig(Map.copyOf(units));
    }

    public Stats stats(Reinforcement unit) {
        return units.get(unit);
    }
}
