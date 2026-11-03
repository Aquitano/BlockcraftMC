package me.blockcraft.sternenfront.vehicle;

import org.bukkit.configuration.ConfigurationSection;

public record SpeederConfig(boolean enabled, double health, int respawnTicks, double speed,
                            double acceleration, double turnDegrees, int idleReturnTicks) {

    public static SpeederConfig from(ConfigurationSection root) {
        return new SpeederConfig(root.getBoolean("speeders.enabled", true),
                Math.max(1, root.getDouble("speeders.health", 60)),
                Math.max(20, root.getInt("speeders.respawn-seconds", 45) * 20),
                Math.clamp(root.getDouble("speeders.speed", 0.65), 0.1, 1.2),
                Math.clamp(root.getDouble("speeders.acceleration", 0.035), 0.005, 0.1),
                Math.clamp(root.getDouble("speeders.turn-degrees", 4), 1, 10),
                Math.max(20, root.getInt("speeders.idle-return-seconds", 45) * 20));
    }
}
