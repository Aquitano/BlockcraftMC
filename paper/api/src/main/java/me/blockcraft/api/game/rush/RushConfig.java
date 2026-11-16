package me.blockcraft.api.game.rush;

import me.blockcraft.api.config.BundledDefaults;
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

    public record Kit(int blocks, int knockbackLevel) {
        public Kit {
            if (blocks < 1 || blocks > 64 || knockbackLevel < 1 || knockbackLevel > 5) {
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
        var read = BundledDefaults.strict(config);
        if (read.integer("players.min") != 2 || read.integer("countdown.lobby") < 1 || read.integer("countdown.restart") < 2) {
            throw new IllegalArgumentException("MLG Rush needs two players and positive countdowns (restart >= 2)");
        }
        var arena = read.section("arena");
        return new RushConfig(read.integer("score-target"),
                new ArenaSettings(arena.integer("build-margin"), arena.integer("build-height"), arena.integer("floor-y"),
                        arena.integer("void-y")),
                new Kit(read.integer("kit.blocks"), read.integer("kit.knockback-level")),
                read.integer("rewards.win"));
    }
}
