package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

/** Everything {@code presentation} tunes: the look of the round's effects and how much of them clients get. */
public record PresentationConfig(boolean effects, Gameplay gameplay, Deployment deployment, Capture capture,
                                 boolean holographicPosts, int liveEffects, Scorch scorch, SaberMark saberMark, int saberTrailTicks, MuzzleFlash muzzleFlash, Bolt bolt,
                                 HeavyBlaster heavyBlaster, Explosion explosion, Damaged damaged, Ambient ambient, Assault assault, Storm storm,
                                 Lightning lightning) {

    /** How long force push and siege mode tint the screen, 0 for not at all. */
    public record Gameplay(int forcePushTicks, int siegeModeTicks) { }

    /** The fade into a deployment, and the banner naming where it lands when {@code briefing} is on. */
    public record Deployment(int fadeTicks, boolean briefing, int bannerTicks) { }

    /** The ring a captured post draws for {@code ticks}, 0 for none. */
    public record Capture(int ticks, double radius, int points) { }

    public record Scorch(int marks, int every, int ticks, int fadeTicks, float scale) { }

    public record SaberMark(int cooldownTicks, int glowTicks, int ticks, int fadeTicks, float scale, double tiltDegrees) { }

    public record MuzzleFlash(int ticks, float scale, float heavyScale) { }

    public record Bolt(float width, float length, float trailSize, double trailStep, int farEvery, float impactFlash,
                       int impactTicks, float impactDust, float ownTrailSize, float ownFlash) { }

    public record HeavyBlaster(int impactSparks, int impactSmoke, float thumpVolume, float impactVolume, float kickDegrees) { }

    public record Explosion(int smokePuffs, int smokeTicks, int debris) { }

    public record Damaged(double below, int intervalTicks) { }

    public record Ambient(int intervalTicks, int range) { }

    public record Assault(int ticks, double distance, double height, float scale, double clearance) { }

    public record Storm(int density, int haze, int intervalTicks, int hazeTicks, double radius, double windSpeed,
                        int gustSeconds, int gustTicks, double gustBoost) { }

    public record Lightning(int minSeconds, int maxSeconds, double distance) { }

    public static PresentationConfig from(ConfigurationSection section) {
        var config = BundledDefaults.strict(section).section("presentation");
        return new PresentationConfig(
                config.flag("effects"),
                new Gameplay(Math.clamp(config.integer("gameplay.force-push-ticks"), 0, 200),
                        Math.clamp(config.integer("gameplay.siege-mode-ticks"), 0, 200)),
                new Deployment(Math.clamp(config.integer("deployment.fade-ticks"), 0, 40), config.flag("deployment.briefing"),
                        Math.clamp(config.integer("deployment.banner-ticks"), 1, 100)),
                new Capture(Math.clamp(config.integer("capture.ticks"), 0, 100), Math.clamp(config.decimal("capture.radius"), 1, 16),
                        Math.clamp(config.integer("capture.points"), 8, 64)),
                config.flag("holographic-posts"),
                Math.max(1, config.integer("live-effects")),
                new Scorch(Math.max(1, config.integer("scorch.marks")),
                        Math.max(1, config.integer("scorch.every")),
                        Math.max(1, config.integer("scorch.ticks")),
                        Math.max(0, config.integer("scorch.fade-ticks")),
                        (float) config.decimal("scorch.scale")),
                new SaberMark(Math.max(0, config.integer("saber-mark.cooldown-ticks")),
                        Math.max(1, config.integer("saber-mark.glow-ticks")),
                        Math.max(1, config.integer("saber-mark.ticks")),
                        Math.max(0, config.integer("saber-mark.fade-ticks")),
                        (float) config.decimal("saber-mark.scale"),
                        Math.max(0, config.decimal("saber-mark.tilt-degrees"))),
                Math.clamp(config.integer("saber-trail-ticks"), 1, 6),
                new MuzzleFlash(Math.max(1, config.integer("muzzle-flash.ticks")),
                        (float) config.decimal("muzzle-flash.scale"),
                        (float) config.decimal("muzzle-flash.heavy-scale")),
                new Bolt((float) config.decimal("bolt.width"),
                        (float) config.decimal("bolt.length"),
                        (float) config.decimal("bolt.trail-size"),
                        Math.max(0.1, config.decimal("bolt.trail-step")),
                        Math.max(1, config.integer("bolt.far-every")),
                        (float) config.decimal("bolt.impact-flash"),
                        Math.max(1, config.integer("bolt.impact-ticks")),
                        (float) Math.max(0.1, config.decimal("bolt.impact-dust")),
                        (float) Math.max(0.1, config.decimal("bolt.own-trail-size")),
                        (float) Math.max(0.1, config.decimal("bolt.own-flash"))),
                new HeavyBlaster(Math.max(0, config.integer("heavy-blaster.impact-sparks")),
                        Math.max(0, config.integer("heavy-blaster.impact-smoke")),
                        (float) Math.max(0, config.decimal("heavy-blaster.thump-volume")),
                        (float) Math.max(0, config.decimal("heavy-blaster.impact-volume")),
                        (float) Math.clamp(config.decimal("heavy-blaster.kick-degrees"), 0, 5)),
                new Explosion(Math.max(0, config.integer("explosion.smoke-puffs")),
                        Math.max(1, config.integer("explosion.smoke-ticks")),
                        Math.max(0, config.integer("explosion.debris"))),
                new Damaged(config.decimal("damaged.below"),
                        Math.max(1, config.integer("damaged.interval-ticks"))),
                new Ambient(Math.max(1, config.integer("ambient.interval-ticks")),
                        Math.max(1, config.integer("ambient.range"))),
                new Assault(Math.max(1, config.integer("assault.ticks")),
                        config.decimal("assault.distance"),
                        config.decimal("assault.height"),
                        (float) config.decimal("assault.scale"),
                        config.decimal("assault.clearance")),
                new Storm(Math.max(0, config.integer("storm.density")),
                        Math.max(0, config.integer("storm.haze")),
                        Math.max(1, config.integer("storm.interval-ticks")),
                        Math.max(1, config.integer("storm.haze-ticks")),
                        Math.max(1, config.decimal("storm.radius")),
                        config.decimal("storm.wind-speed"),
                        Math.max(1, config.integer("storm.gust-seconds")),
                        Math.max(1, config.integer("storm.gust-ticks")),
                        Math.max(0, config.decimal("storm.gust-boost"))),
                new Lightning(Math.max(1, config.integer("lightning.min-seconds")),
                        Math.max(1, config.integer("lightning.max-seconds")),
                        Math.max(1, config.decimal("lightning.distance"))));
    }
}
