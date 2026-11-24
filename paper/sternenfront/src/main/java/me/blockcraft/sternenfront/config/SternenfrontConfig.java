package me.blockcraft.sternenfront.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.common.config.StrictConfig;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.RoundActs;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Sternenfront's own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record SternenfrontConfig(int playersPerTeam, int fullSeconds, int roundSeconds, Rewards rewards, int tickets,
                                 int bleedSeconds, int captureSeconds, int radioCooldownSeconds, Respawn respawn, double headquartersDamage,
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

    /**
     * {@code screenSettleTicks} keep the deploy screen from closing on the click that opened it, and an enemy within
     * {@code enemyClearance} blocks a spawn.
     */
    public record Respawn(int seconds, int autoSeconds, int protectionSeconds, int screenSettleTicks, double enemyClearance) { }

    public record Regen(int delaySeconds, double perSecond) { }

    public record Relic(int firstSeconds, int respawnSeconds, double health, double damage, int leapCooldownSeconds,
                        double leapForward, double leapUp) { }

    /** {@code charges} throws are held at once, each refilled the ability's cooldown after the one before. */
    public record Grenade(int fuseTicks, double radius, double damage, double throwSpeed, int charges) { }

    /** For {@code seconds} the unit stands rooted, and its gun pulls every {@code cooldownTicks} with {@code bolts} bolts and {@code heatScale} of the heat. */
    public record SiegeMode(int seconds, int cooldownTicks, int bolts, double heatScale) {

        public Blaster.Stats apply(Blaster.Stats stats) {
            return new Blaster.Stats(stats.damage(), stats.headshot(), stats.heat() * heatScale, cooldownTicks, bolts,
                    stats.burstTicks(), stats.spread(), stats.speed(), stats.range(), stats.mode());
        }
    }

    public record Shield(int seconds, int width, int height, int distance) { }

    public record JumpPack(double forward, double up) { }

    public record MedStation(int seconds, double radius, double healPerSecond) { }

    public static SternenfrontConfig from(ConfigurationSection section) {
        var config = BundledDefaults.strict(section);
        var abilities = config.section("abilities");
        var siege = abilities.section("siege-mode");
        var shield = abilities.section("shield-wall");
        var jump = abilities.section("jump-pack");
        var med = abilities.section("med-station");
        var relic = config.section("relic");
        var acts = config.section("acts");
        var rewards = config.section("rewards");
        var respawn = config.section("respawn");
        var heat = config.section("heat");
        return new SternenfrontConfig(
                Math.max(1, config.integer("teams.players-per-team")),
                Math.max(1, config.integer("countdown.full")),
                Math.max(60, config.integer("countdown.round")),
                new Rewards(Math.max(0, rewards.integer("kill")), Math.max(0, rewards.integer("bot-kill")),
                        Math.max(0, rewards.integer("capture")), Math.max(0, rewards.integer("champion")),
                        Math.max(0, rewards.integer("award")), Math.max(0, rewards.integer("win"))),
                Math.max(1, config.integer("tickets.start")),
                Math.max(1, config.integer("tickets.bleed-seconds")),
                Math.max(1, config.integer("posts.capture-seconds")),
                Math.clamp(config.integer("posts.radio-cooldown-seconds"), 1, 120),
                new Respawn(Math.max(0, respawn.integer("seconds")), Math.max(1, respawn.integer("auto-seconds")),
                        Math.max(0, respawn.integer("protection-seconds")), Math.clamp(respawn.integer("screen-settle-ticks"), 0, 200),
                        Math.clamp(respawn.decimal("enemy-clearance"), 0, 64)),
                Math.max(0, config.decimal("headquarters.enemy-damage")),
                new Regen(Math.max(1, config.integer("regen.delay-seconds")), Math.max(0, config.decimal("regen.per-second"))),
                new Relic(Math.max(0, relic.integer("first-seconds")), Math.max(1, relic.integer("respawn-seconds")),
                        Math.max(1, relic.decimal("health")), Math.max(1, relic.decimal("damage")),
                        Math.max(1, relic.integer("leap-cooldown-seconds")), relic.decimal("leap-forward"),
                        relic.decimal("leap-up")),
                new Heat.Rules(Math.max(0, heat.integer("cool-delay-ticks")), Math.max(0.1, heat.decimal("cool-per-tick")),
                        Math.max(1, heat.integer("vent-ticks")), Math.clamp(heat.decimal("window-share"), 0, 0.5),
                        Math.max(0, heat.decimal("miss-penalty-share"))),
                byKey(Faction.class, faction -> byKey(CombatClass.class, combatClass ->
                        classStats(config.section("classes").section(faction.key()).section(combatClass.key())))),
                blasters(config.section("blasters")),
                byKey(Ability.class, ability -> Math.max(1, abilities.section(ability.key()).integer("cooldown-seconds"))),
                grenade(abilities.section("grenade")),
                grenade(abilities.section("mining-charge")),
                new SiegeMode(Math.max(1, siege.integer("seconds")), Math.max(1, siege.integer("cooldown-ticks")),
                        Math.max(1, siege.integer("bolts")), Math.max(0, siege.decimal("heat-scale"))),
                Math.max(1, abilities.integer("adrenaline.seconds")),
                new Shield(Math.max(1, shield.integer("seconds")), Math.max(1, shield.integer("width")),
                        Math.max(1, shield.integer("height")), Math.max(1, shield.integer("distance"))),
                new JumpPack(jump.decimal("forward"), jump.decimal("up")),
                new MedStation(Math.max(1, med.integer("seconds")), Math.max(1, med.decimal("radius")),
                        Math.max(0, med.decimal("heal-per-second"))),
                CombatStyle.from(section),
                new RoundActs.Rules(Math.clamp(acts.decimal("assault-share"), 0, 1), Math.max(0, acts.integer("assault-seconds")),
                        Math.clamp(acts.decimal("last-stand-share"), 0, 1), Math.max(0, acts.integer("last-stand-seconds")),
                        Math.max(1, acts.integer("priority-bleed")), Math.max(1, acts.integer("last-stand-bleed")),
                        Math.clamp(acts.decimal("comeback-hero-share"), 0, 1), Math.clamp(acts.decimal("storm-sight-share"), 0, 1),
                        Math.max(0, acts.decimal("priority-pull")), Math.max(1, acts.integer("dropship-distance"))));
    }

    private static ClassStats classStats(StrictConfig section) {
        return new ClassStats(Math.max(1, section.decimal("health")), Math.max(0.1, section.decimal("speed")),
                Math.clamp(section.decimal("scale"), MIN_SCALE, MAX_SCALE));
    }

    private static Grenade grenade(StrictConfig section) {
        return new Grenade(Math.max(1, section.integer("fuse-ticks")), Math.max(1, section.decimal("radius")),
                Math.max(0, section.decimal("damage")), section.decimal("throw-speed"), Math.max(1, section.integer("charges")));
    }

    /**
     * A faction's weapon takes every key it leaves out from its profile's section, which sets them all. The speeder
     * cannon has none: {@link me.blockcraft.sternenfront.vehicle.SpeederConfig} sets its stats.
     */
    private static Map<Blaster, Blaster.Stats> blasters(StrictConfig section) {
        var stats = new EnumMap<Blaster, Blaster.Stats>(Blaster.class);
        Arrays.stream(Blaster.values()).filter(weapon -> weapon != Blaster.SPEEDER_CANNON).forEach(weapon -> {
            var layered = new LinkedHashMap<>(section.section(weapon.profile().key()).values());
            layered.putAll(section.section(weapon.key()).values());
            stats.put(weapon, blaster(new StrictConfig(section.file(), section.path() + "." + weapon.key(), layered)));
        });
        return Map.copyOf(stats);
    }

    private static Blaster.Stats blaster(StrictConfig section) {
        return new Blaster.Stats(
                Math.max(0, section.decimal("damage")),
                Math.max(1, section.decimal("headshot")),
                Math.max(0, section.decimal("heat")),
                Math.max(1, section.integer("cooldown-ticks")),
                Math.max(1, section.integer("bolts")),
                Math.max(0, section.integer("burst-ticks")),
                Math.max(0, section.decimal("spread")),
                Math.max(0.5, section.decimal("speed")),
                Math.max(1, section.decimal("range")),
                Blaster.FireMode.of(section.text("mode")));
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

    private static <K extends Enum<K>, V> Map<K, V> byKey(Class<K> type, Function<K, V> value) {
        var map = new EnumMap<K, V>(type);
        Arrays.stream(type.getEnumConstants()).forEach(constant -> map.put(constant, value.apply(constant)));
        return Map.copyOf(map);
    }
}
