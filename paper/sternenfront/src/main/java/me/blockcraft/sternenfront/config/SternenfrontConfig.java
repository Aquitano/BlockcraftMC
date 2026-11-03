package me.blockcraft.sternenfront.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Heat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** Sternenfront's own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record SternenfrontConfig(int playersPerTeam, int fullSeconds, int roundSeconds, Rewards rewards, int tickets,
                                 int bleedSeconds, int captureSeconds, Respawn respawn, double headquartersDamage,
                                 Regen regen, Relic relic, Heat.Rules heat, Map<CombatClass, Double> health,
                                 Map<Blaster, Blaster.Stats> blasters, Map<Ability, Integer> cooldownSeconds,
                                 Grenade grenade, int adrenalineSeconds, Shield shield, JumpPack jumpPack,
                                 MedStation medStation, CombatStyle combat) {

    public record Rewards(int kill, int capture, int champion, int win) { }

    public record Respawn(int seconds, int autoSeconds, int protectionSeconds) { }

    public record Regen(int delaySeconds, double perSecond) { }

    public record Relic(int firstSeconds, int respawnSeconds, double health, double damage, int leapCooldownSeconds,
                        double leapForward, double leapUp) { }

    public record Grenade(int fuseTicks, double radius, double damage, double throwSpeed) { }

    public record Shield(int seconds, int width, int height, int distance) { }

    public record JumpPack(double forward, double up) { }

    public record MedStation(int seconds, double radius, double healPerSecond) { }

    public static void copyDefaults(ConfigurationSection config) {
        // Bukkit getters with an explicit fallback do not consult the default configuration.
        config.getValues(true).forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection)) {
                config.set(path, value);
            }
        });
    }

    public static SternenfrontConfig from(ConfigurationSection config) {
        var abilities = section(config, "abilities");
        var grenade = section(abilities, "grenade");
        var shield = section(abilities, "shield-wall");
        var jump = section(abilities, "jump-pack");
        var med = section(abilities, "med-station");
        var relic = section(config, "relic");
        return new SternenfrontConfig(
                Math.max(1, config.getInt("teams.players-per-team", 6)),
                Math.max(1, config.getInt("countdown.full", 20)),
                Math.max(60, config.getInt("countdown.round", 1200)),
                new Rewards(Math.max(0, config.getInt("rewards.kill", 5)), Math.max(0, config.getInt("rewards.capture", 4)),
                        Math.max(0, config.getInt("rewards.champion", 10)), Math.max(0, config.getInt("rewards.win", 25))),
                Math.max(1, config.getInt("tickets.start", 150)),
                Math.max(1, config.getInt("tickets.bleed-seconds", 5)),
                Math.max(1, config.getInt("posts.capture-seconds", 12)),
                new Respawn(Math.max(0, config.getInt("respawn.seconds", 5)), Math.max(1, config.getInt("respawn.auto-seconds", 15)),
                        Math.max(0, config.getInt("respawn.protection-seconds", 3))),
                Math.max(0, config.getDouble("headquarters.enemy-damage", 4)),
                new Regen(Math.max(1, config.getInt("regen.delay-seconds", 6)), Math.max(0, config.getDouble("regen.per-second", 1))),
                new Relic(Math.max(0, relic.getInt("first-seconds", 120)), Math.max(1, relic.getInt("respawn-seconds", 60)),
                        Math.max(1, relic.getDouble("health", 40)), Math.max(1, relic.getDouble("damage", 9)),
                        Math.max(1, relic.getInt("leap-cooldown-seconds", 6)), relic.getDouble("leap-forward", 1.5),
                        relic.getDouble("leap-up", 0.7)),
                new Heat.Rules(Math.max(0, config.getInt("heat.cool-delay-ticks", 8)), Math.max(0.1, config.getDouble("heat.cool-per-tick", 3)),
                        Math.max(1, config.getInt("heat.vent-ticks", 40))),
                byKey(CombatClass.class, combatClass ->
                        Math.max(1, config.getDouble("classes." + combatClass.key() + ".health", 20))),
                byKey(Blaster.class, blaster -> blaster(section(section(config, "blasters"), blaster.key()))),
                byKey(Ability.class, ability -> Math.max(1, section(abilities, ability.key()).getInt("cooldown-seconds", 20))),
                new Grenade(Math.max(1, grenade.getInt("fuse-ticks", 50)), Math.max(1, grenade.getDouble("radius", 5)),
                        Math.max(0, grenade.getDouble("damage", 16)), grenade.getDouble("throw-speed", 1.1)),
                Math.max(1, section(abilities, "adrenaline").getInt("seconds", 5)),
                new Shield(Math.max(1, shield.getInt("seconds", 8)), Math.max(1, shield.getInt("width", 5)),
                        Math.max(1, shield.getInt("height", 3)), Math.max(1, shield.getInt("distance", 2))),
                new JumpPack(jump.getDouble("forward", 1.3), jump.getDouble("up", 0.9)),
                new MedStation(Math.max(1, med.getInt("seconds", 10)), Math.max(1, med.getDouble("radius", 4)),
                        Math.max(0, med.getDouble("heal-per-second", 2))),
                CombatStyle.from(config));
    }

    private static Blaster.Stats blaster(ConfigurationSection section) {
        return new Blaster.Stats(
                Math.max(0, section.getDouble("damage", 4)),
                Math.max(1, section.getDouble("headshot", 1.5)),
                Math.max(0, section.getDouble("heat", 10)),
                Math.max(1, section.getInt("cooldown-ticks", 4)),
                Math.max(1, section.getInt("bolts", 1)),
                Math.max(0, section.getInt("burst-ticks", 0)),
                Math.max(0, section.getDouble("spread", 0)),
                Math.max(0.5, section.getDouble("speed", 3.5)),
                Math.max(1, section.getDouble("range", 80)));
    }

    public double health(CombatClass combatClass) {
        return health.get(combatClass);
    }

    public Blaster.Stats stats(Blaster blaster) {
        return blasters.get(blaster);
    }

    public int cooldownTicks(Ability ability) {
        return cooldownSeconds.get(ability) * 20;
    }

    public int capacity() {
        return playersPerTeam * 2;
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        var section = parent.getConfigurationSection(key);
        return section == null ? new MemoryConfiguration() : section;
    }

    private static <K extends Enum<K>, V> Map<K, V> byKey(Class<K> type, Function<K, V> value) {
        var map = new EnumMap<K, V>(type);
        Arrays.stream(type.getEnumConstants()).forEach(constant -> map.put(constant, value.apply(constant)));
        return Map.copyOf(map);
    }
}
