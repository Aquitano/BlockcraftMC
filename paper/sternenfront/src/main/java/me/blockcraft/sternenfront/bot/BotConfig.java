package me.blockcraft.sternenfront.bot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public record BotConfig(boolean enabled, int perTeam, double range, double visionAngle, int memoryTicks,
                        double hearingRange, int alarmTicks,
                        double idleTurnDegrees, double stepHeight, int boundaryGraceTicks,
                        int killPoints, int healTicks, double healAmount,
                        Medic medic, Cover cover, Voice voice, Squad squad, BotDifficulty difficulty,
                        Map<BotDifficulty, Tuning> profiles) {

    public record Medic(int pursuitTicks, int retryTicks) { }

    public record Cover(double searchRadius, int holdTicks, int peekTicks) { }

    public record Squad(int fireteamSize, double switchMargin, double spacing, double stagger, double regroupDistance,
                        double catchUpDistance, double walkSpeed, double sprintSpeed, double boundDistance,
                        int boundTicks, int flankAfterTicks, double followRange, double reinforcePull,
                        double fallBackRange, int fallBackTicks, int rallyTicks) { }

    public record Voice(boolean enabled, double range, float volume, int soldierCooldownTicks, int globalCooldownTicks) { }

    public record Tuning(int reactionTicks, int shotTicks, double spread, int burstShots, int burstPauseTicks,
                         double trackingDegrees, double damageScale, boolean headshots, int settleTicks, double settleSpread) { }

    public static BotConfig from(ConfigurationSection config, int capacity) {
        var profiles = new EnumMap<BotDifficulty, Tuning>(BotDifficulty.class);
        for (var difficulty : BotDifficulty.values()) {
            var fallback = switch (difficulty) {
                case EASY -> new Tuning(18, 12, 0.11, 3, 30, 12, 0.6, false, 40, 3);
                case NORMAL -> new Tuning(12, 8, 0.045, 5, 12, 45, 0.9, false, 30, 2.5);
                case VETERAN -> new Tuning(10, 8, 0.02, 0, 0, 180, 1, true, 16, 1.6);
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
                    config.getBoolean(key + "headshots", fallback.headshots()),
                    Math.clamp(config.getInt(key + "aim-settle-ticks", fallback.settleTicks()), 0, 200),
                    Math.clamp(config.getDouble(key + "aim-settle-spread", fallback.settleSpread()), 1, 5)));
        }
        return new BotConfig(config.getBoolean("bots.enabled", true),
                Math.clamp(config.getInt("bots.fill-per-team", 6), 1, capacity),
                Math.clamp(config.getDouble("bots.sight-range", 32), 4, 64),
                Math.clamp(config.getDouble("bots.vision-angle", 120), 30, 180),
                Math.clamp(config.getInt("bots.memory-ticks", 40), 0, 200),
                Math.clamp(config.getDouble("bots.hearing-range", 24), 0, 64),
                Math.clamp(config.getInt("bots.alarm-ticks", 100), 20, 400),
                Math.clamp(config.getDouble("bots.idle-turn-degrees", 12), 1, 45),
                Math.clamp(config.getDouble("bots.step-height", 1), 0.6, 1),
                Math.clamp(config.getInt("bots.boundary-grace-ticks", 40), 1, 200),
                Math.max(0, config.getInt("bots.kill-points", 25)),
                Math.max(1, config.getInt("bots.medic.heal-ticks", 60)),
                Math.max(0, config.getDouble("bots.medic.heal-amount", 3)),
                new Medic(Math.clamp(config.getInt("bots.medic.pursuit-ticks", 80), 20, 200),
                        Math.clamp(config.getInt("bots.medic.retry-ticks", 100), 20, 400)),
                new Cover(Math.clamp(config.getDouble("bots.cover.search-radius", 10), 3, 16),
                        Math.clamp(config.getInt("bots.cover.hold-ticks", 40), 20, 200),
                        Math.clamp(config.getInt("bots.cover.peek-ticks", 40), 20, 100)),
                new Voice(config.getBoolean("bots.voice.enabled", true),
                        Math.clamp(config.getDouble("bots.voice.range", 18), 4, 32),
                        (float) Math.clamp(config.getDouble("bots.voice.volume", 0.65), 0, 1),
                        Math.max(100, config.getInt("bots.voice.soldier-cooldown-ticks", 360)),
                        Math.max(60, config.getInt("bots.voice.global-cooldown-ticks", 100))),
                new Squad(Math.clamp(config.getInt("bots.squad.fireteam-size", 3), 2, 4),
                        Math.clamp(config.getDouble("bots.squad.switch-margin", 25), 0, 100),
                        Math.clamp(config.getDouble("bots.squad.spacing", 3), 1.5, 8),
                        Math.clamp(config.getDouble("bots.squad.stagger", 2), 0, 6),
                        Math.clamp(config.getDouble("bots.squad.regroup-distance", 12), 4, 32),
                        Math.clamp(config.getDouble("bots.squad.catch-up-distance", 32), 8, 96),
                        Math.clamp(config.getDouble("bots.squad.walk-speed", 0.9), 0.5, 1.5),
                        Math.clamp(config.getDouble("bots.squad.sprint-speed", 1.25), 0.5, 1.5),
                        Math.clamp(config.getDouble("bots.squad.bound-distance", 8), 3, 24),
                        Math.clamp(config.getInt("bots.squad.bound-ticks", 80), 20, 200),
                        Math.clamp(config.getInt("bots.squad.flank-after-ticks", 160), 40, 1200),
                        Math.clamp(config.getDouble("bots.squad.follow-range", 12), 0, 48),
                        Math.clamp(config.getDouble("bots.squad.reinforce-pull", 80), 0, 200),
                        Math.clamp(config.getDouble("bots.squad.fall-back-range", 16), 4, 32),
                        Math.clamp(config.getInt("bots.squad.fall-back-ticks", 160), 40, 600),
                        Math.clamp(config.getInt("bots.squad.rally-ticks", 400), 0, 2400)),
                BotDifficulty.named(config.getString("bots.difficulty", "normal")).orElse(BotDifficulty.NORMAL),
                Map.copyOf(profiles));
    }

    public int needed(int humans) {
        return enabled ? Math.max(0, perTeam - humans) : 0;
    }
}
