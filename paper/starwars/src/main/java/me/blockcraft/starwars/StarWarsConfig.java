package me.blockcraft.starwars;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

/** The practice range, the scenery's world, and how blasters cool and their projectiles fly, read once at startup. */
record StarWarsConfig(Practice practice, String sceneryWorld, double coolPerTick, int coolDelayTicks, double rocketSpeed,
                      double grenadeSpeed, int grenadeFuseTicks, double explosionRadius) {

    /** Loaned weapons work only inside {@code area} of {@code world}, and only while {@code enabled}. */
    record Practice(boolean enabled, String world, BoundingBox area) { }

    static StarWarsConfig from(ConfigurationSection yaml) {
        var config = BundledDefaults.strict(yaml);
        var practice = config.section("practice");
        return new StarWarsConfig(
                new Practice(practice.flag("enabled"), practice.text("world"), new BoundingBox(
                        practice.decimal("min.x"), practice.decimal("min.y"), practice.decimal("min.z"),
                        practice.decimal("max.x"), practice.decimal("max.y"), practice.decimal("max.z"))),
                config.text("scenery.world"),
                Math.clamp(config.decimal("heat.cool-per-tick"), .1, 10),
                Math.clamp(config.integer("heat.delay-ticks"), 0, 100),
                Math.clamp(config.decimal("projectiles.rocket-speed"), .1, 2),
                Math.clamp(config.decimal("projectiles.grenade-speed"), .1, 2),
                Math.clamp(config.integer("projectiles.grenade-fuse-ticks"), 10, 200),
                Math.clamp(config.decimal("projectiles.explosion-radius"), 1, 8));
    }
}
