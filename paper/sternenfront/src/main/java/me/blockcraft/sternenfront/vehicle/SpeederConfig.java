package me.blockcraft.sternenfront.vehicle;

import me.blockcraft.sternenfront.game.Blaster;
import org.bukkit.configuration.ConfigurationSection;

public record SpeederConfig(boolean enabled, double health, int respawnTicks, double speed,
                            SpeederMotion.Handling handling, SpeederMotion.Steering steering, int idleReturnTicks,
                            int perFaction, double damagedShare, SpeederMotion.BoostRules boost, Cannon cannon,
                            Ram ram, Explosion explosion, Presentation presentation) {

    public record Cannon(Blaster.Stats stats, double muzzleAhead, double muzzleHeight) { }

    public record Ram(double minimumSpeed, double damageAtTopSpeed, double craftDamage, double knockback, int cooldownTicks) { }

    public record Explosion(double radius, double damage) { }

    public record Presentation(double modelHeight, double pivotHeight, double tail, double bankDegrees,
                               double surgePitchDegrees, double climbPitchDegrees, double bobHeight, int bobPeriodTicks,
                               double smoothing, int teleportTicks) { }

    public static SpeederConfig from(ConfigurationSection root) {
        double speed = Math.clamp(root.getDouble("speeders.speed", 0.65), 0.1, 1.2);
        return new SpeederConfig(root.getBoolean("speeders.enabled", true),
                Math.max(1, root.getDouble("speeders.health", 60)),
                Math.max(20, root.getInt("speeders.respawn-seconds", 45) * 20),
                speed,
                new SpeederMotion.Handling(speed * Math.clamp(root.getDouble("speeders.reverse-share", 0.35), 0.05, 1),
                        Math.clamp(root.getDouble("speeders.acceleration", 0.035), 0.005, 0.1),
                        Math.clamp(root.getDouble("speeders.braking", 0.07), 0.005, 0.3)),
                new SpeederMotion.Steering(Math.clamp(root.getDouble("speeders.turn-degrees", 4), 1, 10),
                        Math.clamp(root.getDouble("speeders.standstill-turn-share", 0.15), 0, 1),
                        Math.clamp(root.getDouble("speeders.full-turn-speed", 0.35), 0.05, 1.6)),
                Math.max(20, root.getInt("speeders.idle-return-seconds", 45) * 20),
                Math.clamp(root.getInt("speeders.per-faction", 2), 1, 3),
                Math.clamp(root.getDouble("speeders.damaged-share", 0.5), 0, 1),
                new SpeederMotion.BoostRules(Math.clamp(root.getDouble("speeders.boost.speed", 0.95), speed, 1.6),
                        1d / Math.max(1, root.getInt("speeders.boost.ticks", 60)),
                        1d / Math.max(1, root.getInt("speeders.boost.recharge-ticks", 200)),
                        Math.clamp(root.getDouble("speeders.boost.restart-share", 0.25), 0, 1)),
                new Cannon(new Blaster.Stats(Math.max(0, root.getDouble("speeders.cannon.damage", 4)), 1,
                        Math.max(0, root.getDouble("speeders.cannon.heat", 7)),
                        Math.max(1, root.getInt("speeders.cannon.cooldown-ticks", 4)), 1, 0, 0,
                        Math.max(0.5, root.getDouble("speeders.cannon.speed", 4.5)),
                        Math.max(1, root.getDouble("speeders.cannon.range", 90))),
                        root.getDouble("speeders.cannon.muzzle-ahead", 1.6),
                        root.getDouble("speeders.cannon.muzzle-height", 0.6)),
                new Ram(Math.max(0, root.getDouble("speeders.ram.minimum-speed", 0.4)),
                        Math.max(0, root.getDouble("speeders.ram.damage-at-top-speed", 12)),
                        Math.max(0, root.getDouble("speeders.ram.craft-damage", 3)),
                        Math.max(0, root.getDouble("speeders.ram.knockback", 0.9)),
                        Math.max(1, root.getInt("speeders.ram.cooldown-ticks", 20))),
                new Explosion(Math.max(0.1, root.getDouble("speeders.explosion.radius", 5)),
                        Math.max(0, root.getDouble("speeders.explosion.damage", 16))),
                new Presentation(root.getDouble("speeders.presentation.model-height", 0.15),
                        root.getDouble("speeders.presentation.pivot-height", 0.4),
                        Math.max(0, root.getDouble("speeders.presentation.tail", 1.2)),
                        root.getDouble("speeders.presentation.bank-degrees", 18),
                        root.getDouble("speeders.presentation.surge-pitch-degrees", 5),
                        root.getDouble("speeders.presentation.climb-pitch-degrees", 14),
                        Math.max(0, root.getDouble("speeders.presentation.bob-height", 0.04)),
                        Math.max(2, root.getInt("speeders.presentation.bob-period-ticks", 50)),
                        Math.clamp(root.getDouble("speeders.presentation.smoothing", 0.25), 0.05, 1),
                        Math.clamp(root.getInt("speeders.presentation.teleport-ticks", 1), 0, 59)));
    }
}
