package me.blockcraft.knockbackffa;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;

record FfaConfig(int capacity, int knockback, Duration creditWindow, Duration spawnProtection,
                 int killCoins, Duration rewardCooldown, Kits kits, boolean launch, int launchSeconds, int rotationSeconds) {

    record Kits(int intervalSeconds, int ammo, int pearls, double projectilePush, int grappleCooldownTicks,
                int heavyKnockback, double grapplePull, double grappleLift, int warningSeconds) {

        static Kits from(ConfigurationSection config) {
            return new Kits(Math.clamp(config.getInt("kits.interval-seconds", 120), 20, 3600),
                    Math.clamp(config.getInt("kits.ammo", 16), 1, 16),
                    Math.clamp(config.getInt("kits.pearls", 3), 1, 16),
                    Math.clamp(config.getDouble("kits.projectile-push", 0.6), 0, 3),
                    Math.clamp(config.getInt("kits.grapple-cooldown-seconds", 3), 0, 30) * 20,
                    Math.clamp(config.getInt("kits.heavy-knockback-level", 3), 1, 5),
                    Math.clamp(config.getDouble("kits.grapple-pull", 1.6), 0, 4),
                    Math.clamp(config.getDouble("kits.grapple-lift", 0.4), 0, 2),
                    Math.clamp(config.getInt("kits.warning-seconds", 5), 0, 30));
        }
    }

    static FfaConfig from(ConfigurationSection config) {
        return new FfaConfig(Math.clamp(config.getInt("players.max", 24), 2, 100),
                Math.clamp(config.getInt("knockback-level", 2), 1, 5),
                Duration.ofSeconds(Math.clamp(config.getInt("hit-credit-seconds", 10), 1, 60)),
                Duration.ofSeconds(Math.clamp(config.getInt("spawn-protection-seconds", 3), 0, 15)),
                Math.clamp(config.getInt("rewards.kill-coins", 2), 0, 100),
                Duration.ofSeconds(Math.max(1, config.getInt("rewards.same-player-cooldown-seconds", 120))),
                Kits.from(config),
                config.getBoolean("launch.enabled", false),
                Math.clamp(config.getInt("launch.interval-seconds", 100), 20, 600),
                config.getBoolean("rotation.enabled", true) ? Math.clamp(config.getInt("rotation.interval-seconds", 300), 60, 3600) : 0);
    }
}
