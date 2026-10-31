package me.blockcraft.ttt.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.ttt.game.Karma;
import me.blockcraft.ttt.game.Roles;
import me.blockcraft.ttt.shop.ShopItem;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** TTT's own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record TttConfig(int capacity, int roundMinimum, int fullSeconds, int preparationSeconds, int roundSeconds,
                        Roles roles, Karma karma, Rewards rewards, Shop shop, Tester tester, boolean regeneration,
                        CombatStyle combat, LootTable chests) {

    public record Rewards(int kill, int win) {
    }

    /**
     * {@code radarSeconds} a radar points, {@code healSeconds} a heal station heals within {@code healRadius} by
     * {@code healPerSecond}, {@code medipack} health points at once, explosions of {@code explosionPower}.
     */
    public record Shop(int startPoints, Map<ShopItem, Integer> prices, int radarSeconds, int healSeconds,
                       double healRadius, double healPerSecond, double medipack, int creeperArrows,
                       float explosionPower) {

        public int price(ShopItem item) {
            return prices.getOrDefault(item, 1);
        }
    }

    public record Tester(int delaySeconds, int showSeconds, int cooldownSeconds) {
    }

    public static TttConfig from(ConfigurationSection config) {
        var prices = new EnumMap<ShopItem, Integer>(ShopItem.class);
        Arrays.stream(ShopItem.values()).forEach(item ->
                prices.put(item, Math.max(0, config.getInt("shop.prices." + item.key(), 1))));
        var chests = config.getConfigurationSection("chests");
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
                new Shop(Math.max(0, config.getInt("shop.start-points", 2)), Map.copyOf(prices),
                        Math.max(1, config.getInt("shop.radar-seconds", 60)),
                        Math.max(1, config.getInt("shop.heal-station.seconds", 60)),
                        config.getDouble("shop.heal-station.radius", 3.0),
                        config.getDouble("shop.heal-station.per-second", 1.0),
                        config.getDouble("shop.medipack", 10.0),
                        Math.max(1, config.getInt("shop.creeper-arrows", 3)),
                        (float) config.getDouble("shop.explosion-power", 3.0)),
                new Tester(Math.max(1, config.getInt("tester.delay-seconds", 3)),
                        Math.max(1, config.getInt("tester.show-seconds", 4)),
                        Math.max(1, config.getInt("tester.cooldown-seconds", 20))),
                config.getBoolean("natural-regeneration", false),
                CombatStyle.from(config),
                chests == null ? new LootTable(1, 1, List.of()) : LootTable.fromYaml(chests, 1, 1));
    }
}
