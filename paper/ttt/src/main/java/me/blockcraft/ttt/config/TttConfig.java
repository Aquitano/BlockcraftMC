package me.blockcraft.ttt.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.ttt.game.Karma;
import me.blockcraft.ttt.game.Roles;
import me.blockcraft.ttt.shop.ShopItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** TTT's own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record TttConfig(int capacity, int roundMinimum, int fullSeconds, int preparationSeconds, int roundSeconds,
                        Roles roles, Karma karma, Rewards rewards, Shop shop, Tester tester, boolean regeneration,
                        CombatStyle combat, LootTable chests, Tasks tasks) {

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
            return prices.getOrDefault(item, 1);
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

    /** TTT 2's chores and sabotage, as config.yml describes them. */
    public record Tasks(int perPlayer, int holdSeconds, double reach, List<Material> startItems, LootTable loot,
                        Sabotage sabotage) {
    }

    public record Sabotage(int cooldownSeconds, int blackoutSeconds, int doorsSeconds, int testerSeconds,
                           int repairSeconds) {
    }

    public static TttConfig from(ConfigurationSection config) {
        var prices = new EnumMap<ShopItem, Integer>(ShopItem.class);
        Arrays.stream(ShopItem.values()).forEach(item ->
                prices.put(item, Math.max(0, config.getInt("shop.prices." + item.key(), 1))));
        return new TttConfig(
                Math.max(3, config.getInt("players.max", 12)),
                Math.max(2, config.getInt("players.round-minimum", 3)),
                Math.max(1, config.getInt("countdown.full", 15)),
                Math.max(1, config.getInt("countdown.preparation", 30)),
                Math.max(60, config.getInt("countdown.round", 600)),
                new Roles(config.getDouble("roles.traitor-ratio", 0.25), config.getDouble("roles.detective-ratio", 0.125),
                        config.getInt("roles.detective-min-players", 6)),
                new Karma(config.getInt("karma.start", 1000), Math.max(1, config.getInt("karma.max", 1000)),
                        config.getInt("karma.team-kill", 100), config.getInt("karma.enemy-kill", 20),
                        config.getInt("karma.clean-round", 30), Math.clamp(config.getDouble("karma.damage-floor", 0.5), 0.0, 1.0)),
                new Rewards(Math.max(0, config.getInt("rewards.kill", 5)), Math.max(0, config.getInt("rewards.win", 20))),
                shop(config, Map.copyOf(prices)),
                new Tester(Math.max(1, config.getInt("tester.delay-seconds", 3)),
                        Math.max(1, config.getInt("tester.show-seconds", 4)),
                        Math.max(1, config.getInt("tester.cooldown-seconds", 20)),
                        Math.max(1, config.getInt("tester.trap-seconds", 3))),
                config.getBoolean("natural-regeneration", false),
                CombatStyle.from(config),
                loot(config.getConfigurationSection("chests")),
                tasks(config));
    }

    private static Shop shop(ConfigurationSection config, Map<ShopItem, Integer> prices) {
        return new Shop(Math.max(0, config.getInt("shop.start-points", 2)), prices,
                Math.max(1, config.getInt("shop.radar-seconds", 60)),
                new HealStation(Math.max(1, config.getInt("shop.heal-station.seconds", 60)),
                        config.getDouble("shop.heal-station.radius", 3.0),
                        config.getDouble("shop.heal-station.per-second", 1.0)),
                config.getDouble("shop.medipack", 10.0),
                Math.max(1, config.getInt("shop.creeper-arrows", 3)),
                (float) config.getDouble("shop.explosion-power", 3.0),
                Math.max(1, config.getInt("shop.mask-seconds", 60)),
                new TimeBomb(Math.max(5, config.getInt("shop.time-bomb.seconds", 45)),
                        (float) config.getDouble("shop.time-bomb.power", 5.0),
                        Math.clamp(config.getInt("shop.time-bomb.wires", 4), 2, 7)),
                new Gun(Math.max(1, config.getInt("shop.silenced-pistol.shots", 3)),
                        Math.max(0, config.getDouble("shop.silenced-pistol.damage", 8.0)),
                        Math.max(1, config.getDouble("shop.silenced-pistol.range", 40))),
                new Taser(Math.max(1, config.getInt("shop.taser.charges", 2)),
                        Math.max(1, config.getInt("shop.taser.seconds", 4)),
                        Math.max(1, config.getDouble("shop.taser.range", 12))),
                new BearTrap(Math.max(1, config.getInt("shop.bear-trap.seconds", 3)),
                        Math.max(0, config.getDouble("shop.bear-trap.damage", 4.0))),
                new Jammer(Math.max(1, config.getInt("shop.jammer.seconds", 30)),
                        Math.max(1, config.getDouble("shop.jammer.radius", 20.0))),
                Math.max(1, config.getDouble("shop.motion-sensor-radius", 4.0)),
                new Defibrillator(Math.max(1, config.getInt("shop.defibrillator.window-seconds", 30)),
                        Math.max(1, config.getInt("shop.defibrillator.hold-seconds", 3)),
                        Math.clamp(config.getDouble("shop.defibrillator.health", 10.0), 1.0, 20.0)),
                Math.clamp(config.getDouble("shop.vest", 0.3), 0.0, 0.9),
                Math.max(1, config.getDouble("shop.mine-detector-radius", 10.0)),
                Math.max(2, config.getInt("shop.crime-camera-seconds", 6)));
    }

    private static Tasks tasks(ConfigurationSection config) {
        var startItems = config.getStringList("tasks.start-items").stream()
                .map(Material::matchMaterial).filter(Objects::nonNull).toList();
        return new Tasks(Math.max(1, config.getInt("tasks.per-player", 4)),
                Math.max(1, config.getInt("tasks.hold-seconds", 6)),
                Math.max(1, config.getDouble("tasks.reach", 4.5)),
                startItems,
                loot(config.getConfigurationSection("tasks")),
                new Sabotage(Math.max(1, config.getInt("tasks.sabotage.cooldown-seconds", 90)),
                        Math.max(1, config.getInt("tasks.sabotage.blackout-seconds", 40)),
                        Math.max(1, config.getInt("tasks.sabotage.doors-seconds", 15)),
                        Math.max(1, config.getInt("tasks.sabotage.tester-seconds", 60)),
                        Math.max(1, config.getInt("tasks.sabotage.repair-seconds", 5))));
    }

    private static LootTable loot(ConfigurationSection section) {
        return section == null ? new LootTable(1, 1, List.of()) : LootTable.fromYaml(section, 1, 1);
    }
}
