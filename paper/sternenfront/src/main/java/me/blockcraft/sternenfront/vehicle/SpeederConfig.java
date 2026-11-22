package me.blockcraft.sternenfront.vehicle;

import me.blockcraft.api.config.BundledDefaults;
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
        var read = BundledDefaults.strict(root);
        double speed = Math.clamp(read.decimal("speeders.speed"), 0.1, 1.2);
        return new SpeederConfig(read.flag("speeders.enabled"),
                Math.max(1, read.decimal("speeders.health")),
                Math.max(20, read.integer("speeders.respawn-seconds") * 20),
                speed,
                new SpeederMotion.Handling(speed * Math.clamp(read.decimal("speeders.reverse-share"), 0.05, 1),
                        Math.clamp(read.decimal("speeders.acceleration"), 0.005, 0.1),
                        Math.clamp(read.decimal("speeders.braking"), 0.005, 0.3)),
                new SpeederMotion.Steering(Math.clamp(read.decimal("speeders.turn-degrees"), 1, 10),
                        Math.clamp(read.decimal("speeders.standstill-turn-share"), 0, 1),
                        Math.clamp(read.decimal("speeders.full-turn-speed"), 0.05, 1.6)),
                Math.max(20, read.integer("speeders.idle-return-seconds") * 20),
                Math.clamp(read.integer("speeders.per-faction"), 1, 3),
                Math.clamp(read.decimal("speeders.damaged-share"), 0, 1),
                new SpeederMotion.BoostRules(Math.clamp(read.decimal("speeders.boost.speed"), speed, 1.6),
                        1d / Math.max(1, read.integer("speeders.boost.ticks")),
                        1d / Math.max(1, read.integer("speeders.boost.recharge-ticks")),
                        Math.clamp(read.decimal("speeders.boost.restart-share"), 0, 1)),
                new Cannon(new Blaster.Stats(Math.max(0, read.decimal("speeders.cannon.damage")), 1,
                        Math.max(0, read.decimal("speeders.cannon.heat")),
                        Math.max(1, read.integer("speeders.cannon.cooldown-ticks")), 1, 0, 0,
                        Math.max(0.5, read.decimal("speeders.cannon.speed")),
                        Math.max(1, read.decimal("speeders.cannon.range")), Blaster.FireMode.AUTOMATIC),
                        read.decimal("speeders.cannon.muzzle-ahead"),
                        read.decimal("speeders.cannon.muzzle-height")),
                new Ram(Math.max(0, read.decimal("speeders.ram.minimum-speed")),
                        Math.max(0, read.decimal("speeders.ram.damage-at-top-speed")),
                        Math.max(0, read.decimal("speeders.ram.craft-damage")),
                        Math.max(0, read.decimal("speeders.ram.knockback")),
                        Math.max(1, read.integer("speeders.ram.cooldown-ticks"))),
                new Explosion(Math.max(0.1, read.decimal("speeders.explosion.radius")),
                        Math.max(0, read.decimal("speeders.explosion.damage"))),
                new Presentation(read.decimal("speeders.presentation.model-height"),
                        read.decimal("speeders.presentation.pivot-height"),
                        Math.max(0, read.decimal("speeders.presentation.tail")),
                        read.decimal("speeders.presentation.bank-degrees"),
                        read.decimal("speeders.presentation.surge-pitch-degrees"),
                        read.decimal("speeders.presentation.climb-pitch-degrees"),
                        Math.max(0, read.decimal("speeders.presentation.bob-height")),
                        Math.max(2, read.integer("speeders.presentation.bob-period-ticks")),
                        Math.clamp(read.decimal("speeders.presentation.smoothing"), 0.05, 1),
                        Math.clamp(read.integer("speeders.presentation.teleport-ticks"), 0, 59)));
    }
}
