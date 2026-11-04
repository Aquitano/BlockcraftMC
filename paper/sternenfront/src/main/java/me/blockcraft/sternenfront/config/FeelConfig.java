package me.blockcraft.sternenfront.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How long a killed player watches their killer ({@code killcam.ticks}), how many kills in one life earn an orbital
 * strike ({@code streaks.orbital-strike}), and how much a scoped long rifle slows its bearer ({@code scope.slowdown}),
 * which the client shows as a zoom.
 */
public record FeelConfig(int killCamTicks, int orbitalStreak, double scopeSlowdown) {

    public static FeelConfig from(ConfigurationSection config) {
        return new FeelConfig(
                Math.max(0, config.getInt("killcam.ticks", 50)),
                Math.max(1, config.getInt("streaks.orbital-strike", 5)),
                Math.clamp(config.getDouble("scope.slowdown", 0.9), 0, 0.99));
    }
}
