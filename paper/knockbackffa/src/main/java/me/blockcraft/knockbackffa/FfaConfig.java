package me.blockcraft.knockbackffa;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.common.config.StrictConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;

record FfaConfig(int capacity, int knockback, Duration creditWindow, Duration spawnProtection,
                 int killCoins, Duration rewardCooldown, Kits kits, boolean launch, int launchSeconds, int rotationSeconds) {

    record Kits(int intervalSeconds, int ammo, int pearls, int grappleCooldownTicks,
                int heavyKnockback, double grapplePull, double grappleLift, int warningSeconds) {

        static Kits from(StrictConfig config) {
            return new Kits(Math.clamp(config.integer("kits.interval-seconds"), 20, 3600),
                    Math.clamp(config.integer("kits.ammo"), 1, 16),
                    Math.clamp(config.integer("kits.pearls"), 1, 16),
                    Math.clamp(config.integer("kits.grapple-cooldown-seconds"), 0, 30) * 20,
                    Math.clamp(config.integer("kits.heavy-knockback-level"), 1, 5),
                    Math.clamp(config.decimal("kits.grapple-pull"), 0, 4),
                    Math.clamp(config.decimal("kits.grapple-lift"), 0, 2),
                    Math.clamp(config.integer("kits.warning-seconds"), 0, 30));
        }
    }

    static FfaConfig from(ConfigurationSection yaml) {
        var config = BundledDefaults.strict(yaml);
        return new FfaConfig(Math.clamp(config.integer("players.max"), 2, 100),
                Math.clamp(config.integer("knockback-level"), 1, 5),
                Duration.ofSeconds(Math.clamp(config.integer("hit-credit-seconds"), 1, 60)),
                Duration.ofSeconds(Math.clamp(config.integer("spawn-protection-seconds"), 0, 15)),
                Math.clamp(config.integer("rewards.kill-coins"), 0, 100),
                Duration.ofSeconds(Math.max(1, config.integer("rewards.same-player-cooldown-seconds"))),
                Kits.from(config),
                config.flag("launch.enabled"),
                Math.clamp(config.integer("launch.interval-seconds"), 20, 600),
                config.flag("rotation.enabled") ? Math.clamp(config.integer("rotation.interval-seconds"), 60, 3600) : 0);
    }
}
