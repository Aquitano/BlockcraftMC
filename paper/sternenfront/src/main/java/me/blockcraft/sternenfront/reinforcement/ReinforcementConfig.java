package me.blockcraft.sternenfront.reinforcement;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public record ReinforcementConfig(Map<Reinforcement, Stats> units) {

    public record Stats(int cost, int limit, double health, double speed) { }

    public static ReinforcementConfig from(ConfigurationSection config) {
        var units = new EnumMap<Reinforcement, Stats>(Reinforcement.class);
        for (var unit : Reinforcement.values()) {
            var path = "reinforcement-units." + unit.key() + ".";
            units.put(unit, new Stats(Math.max(1, config.getInt(path + "cost", 250)),
                    Math.max(0, config.getInt(path + "limit", 2)),
                    finite(config.getDouble(path + "health", 30), 1, 100, 30),
                    finite(config.getDouble(path + "speed", 1), 0.1, 2, 1)));
        }
        return new ReinforcementConfig(Map.copyOf(units));
    }

    private static double finite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    public Stats stats(Reinforcement unit) {
        return units.get(unit);
    }
}
