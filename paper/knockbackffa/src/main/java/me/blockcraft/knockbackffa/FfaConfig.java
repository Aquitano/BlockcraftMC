package me.blockcraft.knockbackffa;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;

record FfaConfig(int capacity, int knockback, Duration creditWindow, Duration spawnProtection,
                 int killCoins, Duration rewardCooldown, int pickupSeconds, boolean launch, int launchSeconds) {

    static FfaConfig from(ConfigurationSection config) {
        return new FfaConfig(Math.clamp(config.getInt("players.max", 24), 2, 100),
                Math.clamp(config.getInt("knockback-level", 2), 1, 5),
                Duration.ofSeconds(Math.clamp(config.getInt("hit-credit-seconds", 10), 1, 60)),
                Duration.ofSeconds(Math.clamp(config.getInt("spawn-protection-seconds", 3), 0, 15)),
                Math.clamp(config.getInt("rewards.kill-coins", 2), 0, 100),
                Duration.ofSeconds(Math.max(1, config.getInt("rewards.same-player-cooldown-seconds", 120))),
                Math.clamp(config.getInt("pickups.respawn-seconds", 25), 5, 600),
                config.getBoolean("launch.enabled", false),
                Math.clamp(config.getInt("launch.interval-seconds", 100), 20, 600));
    }
}
