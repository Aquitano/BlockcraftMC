package me.blockcraft.api.game.rush;

import org.bukkit.configuration.ConfigurationSection;

public record RushConfig(int scoreTarget, ArenaSettings arena, Kit kit, int winCoins) {

    public record ArenaSettings(int buildMargin, int buildHeight, int floorY, int voidY) {
        public ArenaSettings {
            if (buildMargin < 1 || buildMargin > 8 || buildHeight < 3 || buildHeight > 32
                    || floorY < 0 || floorY > 200 || voidY < -60 || voidY >= floorY) {
                throw new IllegalArgumentException("Invalid MLG Rush arena bounds or void threshold");
            }
        }
    }

    public record Dimensions(int bridgeLength, int bridgeWidth, int baseRadius, int buildMargin,
                             int buildHeight, int floorY, int voidY) {
        public Dimensions {
            new ArenaSettings(buildMargin, buildHeight, floorY, voidY);
            if (bridgeLength < 3 || bridgeLength > 100 || bridgeWidth < 1 || bridgeWidth > 5
                    || bridgeWidth % 2 == 0 || baseRadius < 3 || baseRadius > 8) {
                throw new IllegalArgumentException("bridge-length must be 3..100, bridge-width 1, 3 or 5, base-radius 3..8");
            }
        }
    }

    public record Kit(int blocks, int knockbackLevel, double horizontalPerLevel,
                      double verticalKnockback, int hitDelayTicks) {
        public Kit {
            if (blocks < 1 || blocks > 64 || knockbackLevel < 1 || knockbackLevel > 5
                    || !Double.isFinite(horizontalPerLevel) || horizontalPerLevel <= 0 || horizontalPerLevel > 2
                    || !Double.isFinite(verticalKnockback) || verticalKnockback < 0 || verticalKnockback > 1
                    || hitDelayTicks < 1 || hitDelayTicks > 20) {
                throw new IllegalArgumentException("Invalid MLG Rush kit settings");
            }
        }
    }

    public RushConfig {
        if (scoreTarget < 1 || scoreTarget > 100 || winCoins < 0) {
            throw new IllegalArgumentException("Invalid MLG Rush score target or reward");
        }
    }

    public static RushConfig from(ConfigurationSection config) {
        if (config.getInt("players.min", 2) != 2 || config.getInt("countdown.lobby", 5) < 1
                || config.getInt("countdown.restart", 10) < 2) {
            throw new IllegalArgumentException("MLG Rush needs two players and positive countdowns (restart >= 2)");
        }
        return new RushConfig(config.getInt("score-target", 5),
                new ArenaSettings(config.getInt("arena.build-margin", 4),
                        config.getInt("arena.build-height", 16), config.getInt("arena.floor-y", 64),
                        config.getInt("arena.void-y", 48)),
                new Kit(config.getInt("kit.blocks", 64), config.getInt("kit.knockback-level", 1),
                        config.getDouble("kit.horizontal-per-level", 0.9),
                        config.getDouble("kit.vertical-knockback", 0.35), config.getInt("kit.hit-delay-ticks", 8)),
                config.getInt("rewards.win", 10));
    }
}
