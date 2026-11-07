package me.blockcraft.sternenfront.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.RoundActs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** Sternenfront's own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record SternenfrontConfig(int playersPerTeam, int fullSeconds, int roundSeconds, Rewards rewards, int tickets,
                                 int bleedSeconds, int captureSeconds, Respawn respawn, double headquartersDamage,
                                 Regen regen, Relic relic, Heat.Rules heat, Map<Faction, Map<CombatClass, ClassStats>> classes,
                                 Map<Blaster, Blaster.Stats> blasters, Map<Ability, Integer> cooldownSeconds,
                                 Grenade grenade, Grenade miningCharge, SiegeMode siegeMode, int adrenalineSeconds,
                                 Shield shield, JumpPack jumpPack, MedStation medStation, CombatStyle combat,
                                 RoundActs.Rules acts) {

    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 2;

    /** A class's body on one faction: its health, {@code speed} times normal movement, {@code scale} times normal size. */
    public record ClassStats(double health, double speed, double scale) { }

    public record Rewards(int kill, int botKill, int capture, int champion, int award, int win) { }

    public record Respawn(int seconds, int autoSeconds, int protectionSeconds) { }

    public record Regen(int delaySeconds, double perSecond) { }

    public record Relic(int firstSeconds, int respawnSeconds, double health, double damage, int leapCooldownSeconds,
                        double leapForward, double leapUp) { }

    /** {@code charges} throws are held at once, each refilled the ability's cooldown after the one before. */
    public record Grenade(int fuseTicks, double radius, double damage, double throwSpeed, int charges) { }

    /** For {@code seconds} the unit stands rooted, and its gun pulls every {@code cooldownTicks} with {@code bolts} bolts and {@code heatScale} of the heat. */
    public record SiegeMode(int seconds, int cooldownTicks, int bolts, double heatScale) {

        public Blaster.Stats apply(Blaster.Stats stats) {
            return new Blaster.Stats(stats.damage(), stats.headshot(), stats.heat() * heatScale, cooldownTicks, bolts,
                    stats.burstTicks(), stats.spread(), stats.speed(), stats.range());
        }
    }

    public record Shield(int seconds, int width, int height, int distance) { }

    public record JumpPack(double forward, double up) { }

    public record MedStation(int seconds, double radius, double healPerSecond) { }

    public static SternenfrontConfig from(ConfigurationSection config) {
        var abilities = section(config, "abilities");
        var classes = section(config, "classes");
        var siege = section(abilities, "siege-mode");
        var shield = section(abilities, "shield-wall");
        var jump = section(abilities, "jump-pack");
        var med = section(abilities, "med-station");
        var relic = section(config, "relic");
        var acts = section(config, "acts");
        return new SternenfrontConfig(
                Math.max(1, config.getInt("teams.players-per-team", 6)),
                Math.max(1, config.getInt("countdown.full", 20)),
                Math.max(60, config.getInt("countdown.round", 1200)),
                new Rewards(Math.max(0, config.getInt("rewards.kill", 5)), Math.max(0, config.getInt("rewards.bot-kill", 1)),
                        Math.max(0, config.getInt("rewards.capture", 4)), Math.max(0, config.getInt("rewards.champion", 10)),
                        Math.max(0, config.getInt("rewards.award", 10)), Math.max(0, config.getInt("rewards.win", 25))),
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
                        Math.max(1, config.getInt("heat.vent-ticks", 40)), Math.clamp(config.getDouble("heat.window-share"), 0, 0.5),
                        Math.max(0, config.getDouble("heat.miss-penalty-share"))),
                byKey(Faction.class, faction -> byKey(CombatClass.class, combatClass ->
                        classStats(section(section(classes, faction.key()), combatClass.key())))),
                blasters(section(config, "blasters")),
                byKey(Ability.class, ability -> Math.max(1, section(abilities, ability.key()).getInt("cooldown-seconds", 20))),
                grenade(section(abilities, "grenade"), new Grenade(50, 5, 16, 1.1, 1)),
                grenade(section(abilities, "mining-charge"), new Grenade(60, 6, 20, 0.9, 1)),
                new SiegeMode(Math.max(1, siege.getInt("seconds", 6)), Math.max(1, siege.getInt("cooldown-ticks", 4)),
                        Math.max(1, siege.getInt("bolts", 2)), Math.max(0, siege.getDouble("heat-scale", 0.5))),
                Math.max(1, section(abilities, "adrenaline").getInt("seconds", 5)),
                new Shield(Math.max(1, shield.getInt("seconds", 8)), Math.max(1, shield.getInt("width", 5)),
                        Math.max(1, shield.getInt("height", 3)), Math.max(1, shield.getInt("distance", 2))),
                new JumpPack(jump.getDouble("forward", 1.3), jump.getDouble("up", 0.9)),
                new MedStation(Math.max(1, med.getInt("seconds", 10)), Math.max(1, med.getDouble("radius", 4)),
                        Math.max(0, med.getDouble("heal-per-second", 2))),
                CombatStyle.from(config),
                new RoundActs.Rules(Math.clamp(acts.getDouble("assault-share", 0.65), 0, 1), Math.max(0, acts.getInt("assault-seconds", 360)),
                        Math.clamp(acts.getDouble("last-stand-share", 0.30), 0, 1), Math.max(0, acts.getInt("last-stand-seconds", 780)),
                        Math.max(1, acts.getInt("priority-bleed", 2)), Math.max(1, acts.getInt("last-stand-bleed", 2)),
                        Math.clamp(acts.getDouble("comeback-hero-share", 0.5), 0, 1), Math.clamp(acts.getDouble("storm-sight-share", 0.6), 0, 1),
                        Math.max(0, acts.getDouble("priority-pull", 60)), Math.max(1, acts.getInt("dropship-distance", 22))));
    }

    private static ClassStats classStats(ConfigurationSection section) {
        return new ClassStats(Math.max(1, section.getDouble("health", 20)), Math.max(0.1, section.getDouble("speed", 1)),
                Math.clamp(section.getDouble("scale", 1), MIN_SCALE, MAX_SCALE));
    }

    private static Grenade grenade(ConfigurationSection section, Grenade fallback) {
        return new Grenade(Math.max(1, section.getInt("fuse-ticks", fallback.fuseTicks())),
                Math.max(1, section.getDouble("radius", fallback.radius())),
                Math.max(0, section.getDouble("damage", fallback.damage())),
                section.getDouble("throw-speed", fallback.throwSpeed()), Math.max(1, section.getInt("charges", fallback.charges())));
    }

    private static Map<Blaster, Blaster.Stats> blasters(ConfigurationSection section) {
        var fallback = new Blaster.Stats(4, 1.5, 10, 4, 1, 0, 0, 3.5, 80);
        return byKey(Blaster.class, weapon -> {
            var profile = blaster(section(section, weapon.profile().key()), fallback);
            return blaster(section(section, weapon.key()), profile);
        });
    }

    private static Blaster.Stats blaster(ConfigurationSection section, Blaster.Stats fallback) {
        return new Blaster.Stats(
                Math.max(0, section.getDouble("damage", fallback.damage())),
                Math.max(1, section.getDouble("headshot", fallback.headshot())),
                Math.max(0, section.getDouble("heat", fallback.heat())),
                Math.max(1, section.getInt("cooldown-ticks", fallback.cooldownTicks())),
                Math.max(1, section.getInt("bolts", fallback.bolts())),
                Math.max(0, section.getInt("burst-ticks", fallback.burstTicks())),
                Math.max(0, section.getDouble("spread", fallback.spread())),
                Math.max(0.5, section.getDouble("speed", fallback.speed())),
                Math.max(1, section.getDouble("range", fallback.range())));
    }

    public ClassStats stats(Faction faction, CombatClass combatClass) {
        return classes.get(faction).get(combatClass);
    }

    public Blaster.Stats stats(Blaster blaster) {
        return blasters.get(blaster);
    }

    public int cooldownTicks(Ability ability) {
        return cooldownSeconds.get(ability) * 20;
    }

    /** How many uses of {@code ability} a fighter holds at once. */
    public int charges(Ability ability) {
        return switch (ability) {
            case GRENADE -> grenade.charges();
            case MINING_CHARGE -> miningCharge.charges();
            default -> 1;
        };
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
