package me.blockcraft.sternenfront.bot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public record BotConfig(boolean enabled, int perTeam, double range, int killPoints, int healTicks, double healAmount,
                        BotDifficulty difficulty, Map<BotDifficulty, Tuning> profiles) {

    public record Tuning(int reactionTicks, int shotTicks, double spread, int burstShots, int burstPauseTicks,
                         double trackingDegrees, double damageScale, boolean headshots) { }

    public static BotConfig from(ConfigurationSection config, int capacity) {
        var profiles = new EnumMap<BotDifficulty, Tuning>(BotDifficulty.class);
        for (var difficulty : BotDifficulty.values()) {
            var fallback = switch (difficulty) {
                case EASY -> new Tuning(18, 12, 0.11, 3, 30, 12, 0.6, false);
                case NORMAL -> new Tuning(12, 8, 0.045, 5, 12, 45, 0.9, false);
                case VETERAN -> new Tuning(10, 8, 0.02, 0, 0, 180, 1, true);
            };
            String key = "bots.profiles." + difficulty.key() + ".";
            profiles.put(difficulty, new Tuning(
                    Math.max(1, config.getInt(key + "reaction-ticks", fallback.reactionTicks())),
                    Math.max(1, config.getInt(key + "shot-ticks", fallback.shotTicks())),
                    Math.clamp(config.getDouble(key + "aim-spread", fallback.spread()), 0, 1),
                    Math.clamp(config.getInt(key + "burst-shots", fallback.burstShots()), 0, 64),
                    Math.max(0, config.getInt(key + "burst-pause-ticks", fallback.burstPauseTicks())),
                    Math.clamp(config.getDouble(key + "tracking-degrees", fallback.trackingDegrees()), 1, 180),
                    Math.clamp(config.getDouble(key + "damage-scale", fallback.damageScale()), 0.05, 1),
                    config.getBoolean(key + "headshots", fallback.headshots())));
        }
        return new BotConfig(config.getBoolean("bots.enabled", true),
                Math.clamp(config.getInt("bots.fill-per-team", 4), 1, capacity),
                Math.clamp(config.getDouble("bots.sight-range", 32), 4, 64),
                Math.max(0, config.getInt("bots.kill-points", 25)),
                Math.max(1, config.getInt("bots.medic.heal-ticks", 60)),
                Math.max(0, config.getDouble("bots.medic.heal-amount", 3)),
                BotDifficulty.named(config.getString("bots.difficulty", "normal")).orElse(BotDifficulty.NORMAL),
                Map.copyOf(profiles));
    }

    public int needed(int humans) {
        return enabled ? Math.max(0, perTeam - humans) : 0;
    }
}
