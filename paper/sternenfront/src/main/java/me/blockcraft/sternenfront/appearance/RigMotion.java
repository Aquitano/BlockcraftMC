package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

record RigMotion(double strideBlocks, int cycleTicks, double legDegrees, double bobHeight,
                 double leanDegrees, double swayDegrees, double recoilDegrees, double recoilBlocks, int recoilTicks) {

    static RigMotion from(ConfigurationSection config, CombatClass role) {
        var section = Objects.requireNonNull(config.getConfigurationSection("appearance.motion." + role.key()));
        return new RigMotion(bounded(section, "stride-blocks", 0.5, 4),
                Math.clamp(section.getInt("cycle-ticks"), 8, 40), bounded(section, "leg-degrees", 0, 50),
                bounded(section, "bob-height", 0, 0.1), bounded(section, "lean-degrees", 0, 20),
                bounded(section, "sway-degrees", 0, 12), bounded(section, "recoil-degrees", 0, 25),
                bounded(section, "recoil-blocks", 0, 0.15), Math.clamp(section.getInt("recoil-ticks"), 2, 12));
    }

    private static double bounded(ConfigurationSection section, String key, double min, double max) {
        double value = section.getDouble(key);
        return Double.isFinite(value) ? Math.clamp(value, min, max) : min;
    }

    double advance(double moved) {
        return Math.min(Math.max(0, moved) / strideBlocks, 1.0 / cycleTicks) * Math.TAU;
    }

    Pose pose(double phase, double moved) {
        double amount = Math.clamp(moved * cycleTicks / strideBlocks, 0, 1);
        return new Pose((float) (Math.cos(phase) * Math.toRadians(legDegrees) * amount),
                (float) (Math.pow(Math.sin(phase), 2) * bobHeight * amount),
                (float) (Math.toRadians(leanDegrees) * amount),
                (float) (Math.cos(phase) * Math.toRadians(swayDegrees) * amount));
    }

    record Pose(float step, float bob, float lean, float sway) { }
}
