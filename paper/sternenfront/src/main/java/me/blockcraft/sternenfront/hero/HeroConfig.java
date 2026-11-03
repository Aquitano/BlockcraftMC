package me.blockcraft.sternenfront.hero;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** The {@code heroes} section of Sternenfront's config.yml, and what killing a hero pays ({@code rewards.hero}). */
public record HeroConfig(int cost, int perFaction, Points points, Map<Hero, Double> health, Saber saber,
                         Map<HeroAbility, Integer> cooldownSeconds, Push push, Throw saberThrow, Grip grip,
                         Jetpack jetpack, Grapple grapple, Orbital orbital, Rally rally, int reward) {

    public record Points(int kill, int capture, int neutralise) { }

    public record Saber(double damage, double blockedShare, Color jedi, Color sith, Color white, int igniteTicks,
                        int humTicks, int leapCooldownSeconds, double leapForward, double leapUp) { }

    public record Push(double range, double angle, double strength, double up, double damage) { }

    public record Throw(double range, double speed, double damage) { }

    public record Grip(double range, int seconds, double damage) { }

    public record Jetpack(int seconds, double thrust) { }

    public record Grapple(double range, double speed) { }

    public record Orbital(int delayTicks, double radius, int blasts, double damage) { }

    public record Rally(double radius, int seconds) { }

    private static final int TICKS_PER_SECOND = 20;

    public static HeroConfig from(ConfigurationSection root) {
        var config = section(root, "heroes");
        var points = section(config, "points");
        var health = section(config, "health");
        var saber = section(config, "saber");
        var colors = section(saber, "colors");
        var abilities = section(config, "abilities");
        var push = section(abilities, "force-push");
        var saberThrow = section(abilities, "saber-throw");
        var grip = section(abilities, "force-grip");
        var jetpack = section(abilities, "jetpack");
        var grapple = section(abilities, "grapple");
        var orbital = section(abilities, "orbital-strike");
        var rally = section(abilities, "rally");
        var healthByHero = new EnumMap<Hero, Double>(Hero.class);
        Arrays.stream(Hero.values()).filter(hero -> hero != Hero.CHAMPION)
                .forEach(hero -> healthByHero.put(hero, Math.max(1, health.getDouble(hero.key(), 50))));
        var cooldowns = new EnumMap<HeroAbility, Integer>(HeroAbility.class);
        Arrays.stream(HeroAbility.values())
                .forEach(ability -> cooldowns.put(ability, Math.max(1, section(abilities, ability.key()).getInt("cooldown-seconds", 15))));
        return new HeroConfig(
                Math.max(0, config.getInt("cost", 600)),
                Math.max(1, config.getInt("per-faction", 1)),
                new Points(Math.max(0, points.getInt("kill", 100)), Math.max(0, points.getInt("capture", 100)),
                        Math.max(0, points.getInt("neutralise", 50))),
                Map.copyOf(healthByHero),
                new Saber(Math.max(0, saber.getDouble("damage", 8)), Math.clamp(saber.getDouble("blocked-share", 0.3), 0, 1),
                        color(colors, "jedi", 0x3D8BFF), color(colors, "sith", 0xFF2A2A), color(colors, "white", 0xF4F8FF),
                        Math.max(0, saber.getInt("ignite-ticks", 6)), Math.max(10, saber.getInt("hum-ticks", 40)),
                        Math.max(1, saber.getInt("leap-cooldown-seconds", 5)), saber.getDouble("leap-forward", 1.4),
                        saber.getDouble("leap-up", 0.7)),
                Map.copyOf(cooldowns),
                new Push(Math.max(1, push.getDouble("range", 7)), Math.clamp(push.getDouble("angle", 70), 1, 180),
                        push.getDouble("strength", 1.7), push.getDouble("up", 0.5), Math.max(0, push.getDouble("damage", 2))),
                new Throw(Math.max(1, saberThrow.getDouble("range", 14)), Math.max(0.2, saberThrow.getDouble("speed", 1.2)),
                        Math.max(0, saberThrow.getDouble("damage", 9))),
                new Grip(Math.max(1, grip.getDouble("range", 14)), Math.max(1, grip.getInt("seconds", 2)),
                        Math.max(0, grip.getDouble("damage", 7))),
                new Jetpack(Math.max(1, jetpack.getInt("seconds", 4)), Math.max(0, jetpack.getDouble("thrust", 0.4))),
                new Grapple(Math.max(1, grapple.getDouble("range", 30)), Math.max(0.2, grapple.getDouble("speed", 2.2))),
                new Orbital(Math.max(1, orbital.getInt("delay-ticks", 60)), Math.max(1, orbital.getDouble("radius", 5)),
                        Math.max(1, orbital.getInt("blasts", 6)), Math.max(0, orbital.getDouble("damage", 24))),
                new Rally(Math.max(1, rally.getDouble("radius", 12)), Math.max(1, rally.getInt("seconds", 6))),
                Math.max(0, root.getInt("rewards.hero", 15)));
    }

    public double health(Hero hero) {
        return health.getOrDefault(hero, 50.0);
    }

    public int cooldownTicks(HeroAbility ability) {
        return cooldownSeconds.get(ability) * TICKS_PER_SECOND;
    }

    private static Color color(ConfigurationSection section, String key, int fallback) {
        var value = section.getString(key, "");
        try {
            return Color.fromRGB(Integer.parseInt(value.replace("#", ""), 16));
        } catch (IllegalArgumentException invalid) {
            return Color.fromRGB(fallback);
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        var section = parent.getConfigurationSection(key);
        return section == null ? new MemoryConfiguration() : section;
    }
}
