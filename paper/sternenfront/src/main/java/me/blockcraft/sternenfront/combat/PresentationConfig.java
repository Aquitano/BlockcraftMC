package me.blockcraft.sternenfront.combat;

import org.bukkit.configuration.ConfigurationSection;

/** Everything {@code presentation} tunes: the look of the round's effects and how much of them clients get. */
public record PresentationConfig(boolean effects, int liveEffects, Scorch scorch, SaberMark saberMark, MuzzleFlash muzzleFlash, Bolt bolt,
                                 Explosion explosion, Damaged damaged, Ambient ambient, Assault assault, Storm storm) {

    public record Scorch(int marks, int every, int ticks, int fadeTicks, float scale) { }

    public record SaberMark(int cooldownTicks, int glowTicks, int ticks, int fadeTicks, float scale, double tiltDegrees) { }

    public record MuzzleFlash(int ticks, float scale, float heavyScale) { }

    public record Bolt(float width, float length, float trailSize, double trailStep, int farEvery, float impactFlash) { }

    public record Explosion(int smokePuffs, int smokeTicks, int debris) { }

    public record Damaged(double below, int intervalTicks) { }

    public record Ambient(int intervalTicks, int range) { }

    public record Assault(int ticks, double distance, double height, float scale, double clearance) { }

    public record Storm(int density, int haze, int intervalTicks, int hazeTicks, double radius, double windSpeed,
                        int gustSeconds, int gustTicks, double gustBoost) { }

    public static PresentationConfig from(ConfigurationSection config) {
        return new PresentationConfig(
                config.getBoolean("presentation.effects"),
                Math.max(1, config.getInt("presentation.live-effects")),
                new Scorch(Math.max(1, config.getInt("presentation.scorch.marks")),
                        Math.max(1, config.getInt("presentation.scorch.every")),
                        Math.max(1, config.getInt("presentation.scorch.ticks")),
                        Math.max(0, config.getInt("presentation.scorch.fade-ticks")),
                        (float) config.getDouble("presentation.scorch.scale")),
                new SaberMark(Math.max(0, config.getInt("presentation.saber-mark.cooldown-ticks")),
                        Math.max(1, config.getInt("presentation.saber-mark.glow-ticks")),
                        Math.max(1, config.getInt("presentation.saber-mark.ticks")),
                        Math.max(0, config.getInt("presentation.saber-mark.fade-ticks")),
                        (float) config.getDouble("presentation.saber-mark.scale"),
                        Math.max(0, config.getDouble("presentation.saber-mark.tilt-degrees"))),
                new MuzzleFlash(Math.max(1, config.getInt("presentation.muzzle-flash.ticks")),
                        (float) config.getDouble("presentation.muzzle-flash.scale"),
                        (float) config.getDouble("presentation.muzzle-flash.heavy-scale")),
                new Bolt((float) config.getDouble("presentation.bolt.width"),
                        (float) config.getDouble("presentation.bolt.length"),
                        (float) config.getDouble("presentation.bolt.trail-size"),
                        Math.max(0.1, config.getDouble("presentation.bolt.trail-step")),
                        Math.max(1, config.getInt("presentation.bolt.far-every")),
                        (float) config.getDouble("presentation.bolt.impact-flash")),
                new Explosion(Math.max(0, config.getInt("presentation.explosion.smoke-puffs")),
                        Math.max(1, config.getInt("presentation.explosion.smoke-ticks")),
                        Math.max(0, config.getInt("presentation.explosion.debris"))),
                new Damaged(config.getDouble("presentation.damaged.below"),
                        Math.max(1, config.getInt("presentation.damaged.interval-ticks"))),
                new Ambient(Math.max(1, config.getInt("presentation.ambient.interval-ticks")),
                        Math.max(1, config.getInt("presentation.ambient.range"))),
                new Assault(Math.max(1, config.getInt("presentation.assault.ticks")),
                        config.getDouble("presentation.assault.distance"),
                        config.getDouble("presentation.assault.height"),
                        (float) config.getDouble("presentation.assault.scale"),
                        config.getDouble("presentation.assault.clearance")),
                new Storm(Math.max(0, config.getInt("presentation.storm.density")),
                        Math.max(0, config.getInt("presentation.storm.haze")),
                        Math.max(1, config.getInt("presentation.storm.interval-ticks")),
                        Math.max(1, config.getInt("presentation.storm.haze-ticks")),
                        Math.max(1, config.getDouble("presentation.storm.radius")),
                        config.getDouble("presentation.storm.wind-speed"),
                        Math.max(1, config.getInt("presentation.storm.gust-seconds")),
                        Math.max(1, config.getInt("presentation.storm.gust-ticks")),
                        Math.max(0, config.getDouble("presentation.storm.gust-boost"))));
    }
}
