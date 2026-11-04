package me.blockcraft.rush.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.rush.kit.Perk;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

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
        ConfigurationSection perkPrices,
        ConfigurationSection shopPrices,
        CombatStyle combat
) {

    /** How many emerald ores each round places into the walls around the search spot, and in which blocks. */
    public record Ores(int count, int radius, Set<Material> hosts) {

        static Ores from(ConfigurationSection config) {
            var hosts = EnumSet.noneOf(Material.class);
            for (var name : config.getStringList("ores.hosts")) {
                var material = Material.matchMaterial(name);
                if (material != null && material.isBlock()) {
                    hosts.add(material);
                }
            }
            return new Ores(config.getInt("ores.count", 40), config.getInt("ores.radius", 30), hosts);
        }
    }

    public int perkPrice(Perk perk) {
        return perkPrices.getInt(perk.key());
    }

    public int shopPrice(String item) {
        return shopPrices.getInt(item);
    }

    public static RushConfig from(FileConfiguration config) {
        return new RushConfig(
                config.getInt("players.max", 6),
                config.getInt("emeralds-per-ore", 12),
                config.getInt("ore-respawn-seconds", 12),
                Ores.from(config),
                config.getInt("countdown.search", 300),
                config.getInt("countdown.shopping", 90),
                config.getInt("countdown.deathmatch", 1200),
                config.getInt("countdown.deathmatch-tracker-after", 300),
                CompassTracker.Settings.from(config),
                config.getInt("lives", 3),
                config.getInt("rewards.kill", 5),
                config.getInt("rewards.win", 15),
                config.getInt("moments.dagobert-emeralds", 100),
                section(config, "perk-prices"),
                section(config, "shop-prices"),
                CombatStyle.from(config));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
