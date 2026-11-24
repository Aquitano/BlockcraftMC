package me.blockcraft.ttt.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.ttt.game.Karma;
import me.blockcraft.ttt.game.Roles;
import me.blockcraft.ttt.shop.ShopItem;
import me.blockcraft.common.config.StrictConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** TTT's own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record TttConfig(int capacity, int roundMinimum, int fullSeconds, int preparationSeconds, int roundSeconds,
                        Roles roles, Karma karma, Rewards rewards, Shop shop, Tester tester, boolean regeneration,
                        double traitorKillHeal, CombatStyle combat, List<LootTable.Entry> chests, Tasks tasks,
                        int reopenSeconds, int roundsBeforeRestart) {

    public record Rewards(int kill, int win) {
    }

    /**
     * {@code radarSeconds} a radar points, {@code medipack} health points at once, explosions of
     * {@code explosionPower}; the rest as config.yml describes each good.
     */
    public record Shop(int startPoints, Map<ShopItem, Integer> prices, int radarSeconds, HealStation healStation,
                       double medipack, int creeperArrows, float explosionPower, int maskSeconds, TimeBomb timeBomb,
                       Gun pistol, Taser taser, BearTrap bearTrap, Jammer jammer, double motionSensorRadius,
                       Defibrillator defibrillator, double vest, double mineDetectorRadius, int crimeCameraSeconds) {

        public int price(ShopItem item) {
            return prices.get(item);
        }
    }

    public record HealStation(int seconds, double radius, double perSecond) {
    }

    public record TimeBomb(int seconds, float power, int wires) {
    }

    public record Gun(int shots, double damage, double range) {
    }

    public record Taser(int charges, int seconds, double range) {
    }

    public record BearTrap(int seconds, double damage) {
    }

    public record Jammer(int seconds, double radius) {
    }

    public record Defibrillator(int windowSeconds, int holdSeconds, double health) {
    }

    /** {@code trapSeconds} the traitor trap's floor stays open. */
    public record Tester(int delaySeconds, int showSeconds, int cooldownSeconds, int trapSeconds) {
    }

    /** TTT 2's chores, sabotage and dusk, as config.yml describes them. */
    public record Tasks(int perPlayer, int holdSeconds, double reach, int spread, List<LootTable.Entry> loot,
                        Sabotage sabotage, Dusk dusk) {
    }

    /** The time of day, in ticks, when the roles are drawn and when the round runs out; it moves evenly between. */
    public record Dusk(long from, long to) {

        public long at(int secondsLeft, int roundSeconds) {
            double gone = 1 - Math.clamp((double) secondsLeft / roundSeconds, 0, 1);
            return Math.round(from + (to - from) * gone);
        }
    }

    public record Sabotage(int cooldownSeconds, int blackoutSeconds, int doorsSeconds, int testerSeconds,
                           int repairSeconds) {
    }

    public static TttConfig from(ConfigurationSection section) {
        var config = BundledDefaults.strict(section);
        var prices = new EnumMap<ShopItem, Integer>(ShopItem.class);
        Arrays.stream(ShopItem.values()).forEach(item ->
                prices.put(item, Math.max(0, config.integer("shop.prices." + item.key()))));
        return new TttConfig(
                Math.max(3, config.integer("players.max")),
                Math.max(2, config.integer("players.round-minimum")),
                Math.max(1, config.integer("countdown.full")),
                Math.max(1, config.integer("countdown.preparation")),
                Math.max(60, config.integer("countdown.round")),
                new Roles(config.decimal("roles.traitor-ratio"), config.decimal("roles.detective-ratio"),
                        config.integer("roles.detective-min-players")),
                new Karma(config.integer("karma.start"), Math.max(1, config.integer("karma.max")),
                        config.integer("karma.team-kill"), config.integer("karma.enemy-kill"),
                        config.integer("karma.clean-round"), Math.clamp(config.decimal("karma.damage-floor"), 0.0, 1.0)),
                new Rewards(Math.max(0, config.integer("rewards.kill")), Math.max(0, config.integer("rewards.win"))),
                shop(config, Map.copyOf(prices)),
                new Tester(Math.max(1, config.integer("tester.delay-seconds")),
                        Math.max(1, config.integer("tester.show-seconds")),
                        Math.max(1, config.integer("tester.cooldown-seconds")),
                        Math.max(1, config.integer("tester.trap-seconds"))),
                config.flag("natural-regeneration"),
                Math.max(0.0, config.decimal("traitor-kill-heal")),
                CombatStyle.from(section),
                LootTable.entries(section.getConfigurationSection("chests")),
                tasks(config, section.getConfigurationSection("tasks")),
                Math.max(1, config.integer("countdown.reopen")),
                Math.max(1, config.integer("rounds-before-restart")));
    }

    private static Shop shop(StrictConfig config, Map<ShopItem, Integer> prices) {
        return new Shop(Math.max(0, config.integer("shop.start-points")), prices,
                Math.max(1, config.integer("shop.radar-seconds")),
                new HealStation(Math.max(1, config.integer("shop.heal-station.seconds")),
                        config.decimal("shop.heal-station.radius"),
                        config.decimal("shop.heal-station.per-second")),
                config.decimal("shop.medipack"),
                Math.max(1, config.integer("shop.creeper-arrows")),
                (float) config.decimal("shop.explosion-power"),
                Math.max(1, config.integer("shop.mask-seconds")),
                new TimeBomb(Math.max(5, config.integer("shop.time-bomb.seconds")),
                        (float) config.decimal("shop.time-bomb.power"),
                        Math.clamp(config.integer("shop.time-bomb.wires"), 2, 7)),
                new Gun(Math.max(1, config.integer("shop.silenced-pistol.shots")),
                        Math.max(0, config.decimal("shop.silenced-pistol.damage")),
                        Math.max(1, config.decimal("shop.silenced-pistol.range"))),
                new Taser(Math.max(1, config.integer("shop.taser.charges")),
                        Math.max(1, config.integer("shop.taser.seconds")),
                        Math.max(1, config.decimal("shop.taser.range"))),
                new BearTrap(Math.max(1, config.integer("shop.bear-trap.seconds")),
                        Math.max(0, config.decimal("shop.bear-trap.damage"))),
                new Jammer(Math.max(1, config.integer("shop.jammer.seconds")),
                        Math.max(1, config.decimal("shop.jammer.radius"))),
                Math.max(1, config.decimal("shop.motion-sensor-radius")),
                new Defibrillator(Math.max(1, config.integer("shop.defibrillator.window-seconds")),
                        Math.max(1, config.integer("shop.defibrillator.hold-seconds")),
                        Math.clamp(config.decimal("shop.defibrillator.health"), 1.0, 20.0)),
                Math.clamp(config.decimal("shop.vest"), 0.0, 0.9),
                Math.max(1, config.decimal("shop.mine-detector-radius")),
                Math.max(2, config.integer("shop.crime-camera-seconds")));
    }

    private static Tasks tasks(StrictConfig config, ConfigurationSection tasks) {
        return new Tasks(Math.max(1, config.integer("tasks.per-player")),
                Math.max(1, config.integer("tasks.hold-seconds")),
                Math.max(1, config.decimal("tasks.reach")),
                Math.max(0, config.integer("tasks.spread")),
                LootTable.entries(tasks),
                new Sabotage(Math.max(1, config.integer("tasks.sabotage.cooldown-seconds")),
                        Math.max(1, config.integer("tasks.sabotage.blackout-seconds")),
                        Math.max(1, config.integer("tasks.sabotage.doors-seconds")),
                        Math.max(1, config.integer("tasks.sabotage.tester-seconds")),
                        Math.max(1, config.integer("tasks.sabotage.repair-seconds"))),
                new Dusk(config.integer("tasks.dusk.from"), config.integer("tasks.dusk.to")));
    }
}
