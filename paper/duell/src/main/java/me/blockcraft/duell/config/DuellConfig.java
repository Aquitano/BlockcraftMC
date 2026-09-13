package me.blockcraft.duell.config;

import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.api.game.LootTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DuellConfig(
        String globalStatsHead,
        Countdowns countdowns,
        int winCoins,
        int vsKillsToEnd,
        Map<DuelMode, List<String>> maps,
        LootTable loot
) {

    public record Countdowns(int lobby, int freeze, int round, int restart) {
    }

    public List<String> maps(DuelMode mode) {
        return maps.getOrDefault(mode, List.of());
    }

    public static DuellConfig from(FileConfiguration config) {
        var countdown = section(config, "countdown");
        var chest = section(config, "sg-chest");
        var maps = new EnumMap<DuelMode, List<String>>(DuelMode.class);
        for (var mode : DuelMode.values()) {
            maps.put(mode, List.copyOf(config.getStringList("maps." + mode.configKey())));
        }
        return new DuellConfig(
                config.getString("global-stats-head", "Kevos"),
                new Countdowns(
                        countdown.getInt("lobby", 30),
                        countdown.getInt("freeze", 5),
                        countdown.getInt("round", 600),
                        countdown.getInt("restart", 10)),
                config.getInt("rewards.win", 10),
                config.getInt("vs.kills-to-end", 3),
                maps,
                LootTable.fromYaml(chest, 1, 6));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
