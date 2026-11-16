package me.blockcraft.rush.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.common.config.StrictConfig;
import me.blockcraft.rush.kit.Perk;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** EmeraldRush's own keys; the ones every game shares are {@link me.blockcraft.api.game.GameConfig}'s. */
public record RushConfig(
        int maxPlayers,
        int emeraldsPerOre,
        int oreRespawnSeconds,
        Ores ores,
        int searchSeconds,
        int shoppingSeconds,
        int deathmatchSeconds,
        int trackerAfterSeconds,
        CompassTracker.Settings tracker,
        int lives,
        int killCoins,
        int winCoins,
        int dagobertEmeralds,
        Map<Perk, Integer> perkPrices,
        Map<String, Integer> shopPrices,
        CombatStyle combat
) {

    /** How many emerald ores each round places into the walls around the search spot, and in which blocks. */
    public record Ores(int count, int radius, Set<Material> hosts) {

        static Ores from(StrictConfig config) {
            var hosts = EnumSet.noneOf(Material.class);
            for (var name : config.texts("ores.hosts")) {
                var material = Material.matchMaterial(name);
                if (material != null && material.isBlock()) {
                    hosts.add(material);
                }
            }
            return new Ores(config.integer("ores.count"), config.integer("ores.radius"), hosts);
        }
    }

    public int perkPrice(Perk perk) {
        return perkPrices.get(perk);
    }

    public int shopPrice(String item) {
        var price = shopPrices.get(item);
        if (price == null) {
            throw new IllegalStateException("config.yml: shop-prices." + item + " is missing");
        }
        return price;
    }

    public static RushConfig from(FileConfiguration yaml) {
        var config = BundledDefaults.strict(yaml);
        var perks = config.section("perk-prices");
        var perkPrices = new EnumMap<Perk, Integer>(Perk.class);
        for (var perk : Perk.values()) {
            perkPrices.put(perk, perks.integer(perk.key()));
        }
        var shop = config.section("shop-prices");
        return new RushConfig(
                config.integer("players.max"),
                config.integer("emeralds-per-ore"),
                config.integer("ore-respawn-seconds"),
                Ores.from(config),
                config.integer("countdown.search"),
                config.integer("countdown.shopping"),
                config.integer("countdown.deathmatch"),
                config.integer("countdown.deathmatch-tracker-after"),
                CompassTracker.Settings.from(yaml),
                config.integer("lives"),
                config.integer("rewards.kill"),
                config.integer("rewards.win"),
                config.integer("moments.dagobert-emeralds"),
                Map.copyOf(perkPrices),
                shop.keys().stream().collect(Collectors.toUnmodifiableMap(item -> item, shop::integer)),
                CombatStyle.from(yaml));
    }
}
