package me.blockcraft.sternenfront.bot;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public record BotConfig(boolean enabled, int perTeam, double range, double visionAngle, int memoryTicks,
                        double hearingRange, int alarmTicks,
                        double idleTurnDegrees, double stepHeight, int boundaryGraceTicks,
                        int killPoints, int healTicks, double healAmount,
                        Medic medic, Cover cover, Voice voice, HurtSound hurtSound, Squad squad, BotDifficulty difficulty,
                        Map<BotDifficulty, Tuning> profiles) {

    public record Medic(int pursuitTicks, int retryTicks) { }

    public record Cover(double searchRadius, int holdTicks, int peekTicks) { }

    public record Squad(int fireteamSize, double switchMargin, double spacing, double stagger, double regroupDistance,
                        double catchUpDistance, double walkSpeed, double sprintSpeed, double boundDistance,
                        int boundTicks, int flankAfterTicks, double followRange, double reinforcePull,
                        double fallBackRange, int fallBackTicks, int standTicks) { }

    public record Voice(boolean enabled, double range, float volume, int soldierCooldownTicks, int globalCooldownTicks) { }

    public record HurtSound(float volume, int cooldownTicks) { }

    public record Tuning(int reactionTicks, int shotTicks, double spread, int burstShots, int burstPauseTicks,
                         double trackingDegrees, double damageScale, boolean headshots, int settleTicks, double settleSpread) { }

    public static BotConfig from(ConfigurationSection config, int capacity) {
        var profiles = new EnumMap<BotDifficulty, Tuning>(BotDifficulty.class);
        for (var difficulty : BotDifficulty.values()) {
            String key = "bots.profiles." + difficulty.key() + ".";
            profiles.put(difficulty, new Tuning(
                    Math.max(1, config.getInt(key + "reaction-ticks")),
                    Math.max(1, config.getInt(key + "shot-ticks")),
                    Math.clamp(config.getDouble(key + "aim-spread"), 0, 1),
                    Math.clamp(config.getInt(key + "burst-shots"), 0, 64),
                    Math.max(0, config.getInt(key + "burst-pause-ticks")),
                    Math.clamp(config.getDouble(key + "tracking-degrees"), 1, 180),
                    Math.clamp(config.getDouble(key + "damage-scale"), 0.05, 1),
                    config.getBoolean(key + "headshots"),
                    Math.clamp(config.getInt(key + "aim-settle-ticks"), 0, 200),
                    Math.clamp(config.getDouble(key + "aim-settle-spread"), 1, 5)));
        }
        return new BotConfig(config.getBoolean("bots.enabled"),
                Math.clamp(config.getInt("bots.fill-per-team"), 1, capacity),
                Math.clamp(config.getDouble("bots.sight-range"), 4, 64),
                Math.clamp(config.getDouble("bots.vision-angle"), 30, 180),
                Math.clamp(config.getInt("bots.memory-ticks"), 0, 200),
                Math.clamp(config.getDouble("bots.hearing-range"), 0, 64),
                Math.clamp(config.getInt("bots.alarm-ticks"), 20, 400),
                Math.clamp(config.getDouble("bots.idle-turn-degrees"), 1, 45),
                Math.clamp(config.getDouble("bots.step-height"), 0.6, 1),
                Math.clamp(config.getInt("bots.boundary-grace-ticks"), 1, 200),
                Math.max(0, config.getInt("bots.kill-points")),
                Math.max(1, config.getInt("bots.medic.heal-ticks")),
                Math.max(0, config.getDouble("bots.medic.heal-amount")),
                new Medic(Math.clamp(config.getInt("bots.medic.pursuit-ticks"), 20, 200),
                        Math.clamp(config.getInt("bots.medic.retry-ticks"), 20, 400)),
                new Cover(Math.clamp(config.getDouble("bots.cover.search-radius"), 3, 16),
                        Math.clamp(config.getInt("bots.cover.hold-ticks"), 20, 200),
                        Math.clamp(config.getInt("bots.cover.peek-ticks"), 20, 100)),
                new Voice(config.getBoolean("bots.voice.enabled"),
                        Math.clamp(config.getDouble("bots.voice.range"), 4, 32),
                        (float) Math.clamp(config.getDouble("bots.voice.volume"), 0, 1),
                        Math.max(100, config.getInt("bots.voice.soldier-cooldown-ticks")),
                        Math.max(60, config.getInt("bots.voice.global-cooldown-ticks"))),
                new HurtSound((float) Math.clamp(config.getDouble("bots.hurt-sound.volume"), 0, 1),
                        Math.clamp(config.getInt("bots.hurt-sound.cooldown-ticks"), 1, 40)),
                new Squad(Math.clamp(config.getInt("bots.squad.fireteam-size"), 2, 4),
                        Math.clamp(config.getDouble("bots.squad.switch-margin"), 0, 100),
                        Math.clamp(config.getDouble("bots.squad.spacing"), 1.5, 8),
                        Math.clamp(config.getDouble("bots.squad.stagger"), 0, 6),
                        Math.clamp(config.getDouble("bots.squad.regroup-distance"), 4, 32),
                        Math.clamp(config.getDouble("bots.squad.catch-up-distance"),
                                Math.clamp(config.getDouble("bots.squad.regroup-distance"), 8, 32), 96),
                        Math.clamp(config.getDouble("bots.squad.walk-speed"), 0.5, 1.5),
                        Math.clamp(config.getDouble("bots.squad.sprint-speed"),
                                Math.clamp(config.getDouble("bots.squad.walk-speed"), 0.5, 1.5), 1.5),
                        Math.clamp(config.getDouble("bots.squad.bound-distance"), 3, 24),
                        Math.clamp(config.getInt("bots.squad.bound-ticks"), 20, 200),
                        Math.clamp(config.getInt("bots.squad.flank-after-ticks"), 40, 1200),
                        Math.clamp(config.getDouble("bots.squad.follow-range"), 0, 48),
                        Math.clamp(config.getDouble("bots.squad.reinforce-pull"), 0, 200),
                        Math.clamp(config.getDouble("bots.squad.fall-back-range"), 4, 32),
                        Math.clamp(config.getInt("bots.squad.fall-back-ticks"), 40, 600),
                        Math.clamp(config.getInt("bots.squad.stand-ticks"), 60, 1200)),
                BotDifficulty.named(config.getString("bots.difficulty", "")).orElse(BotDifficulty.NORMAL),
                Map.copyOf(profiles));
    }

    public int needed(int humans) {
        return enabled ? Math.max(0, perTeam - humans) : 0;
    }
}
