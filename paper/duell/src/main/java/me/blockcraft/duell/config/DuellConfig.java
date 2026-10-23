package me.blockcraft.duell.config;

import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Duell's own keys; the ones every game shares are {@link me.blockcraft.api.game.GameConfig}'s. */
public record DuellConfig(
        Countdowns countdowns,
        int killCoins,
        int winCoins,
        int vsKillsToEnd,
        Map<DuelMode, List<String>> maps,
        LootTable loot,
        CombatStyle combat,
        Duration rematchWindow,
        SumoConfig sumo,
        Duration tourIntermission
) {

    public DuellConfig {
        if (tourIntermission.compareTo(Duration.ofSeconds(1)) < 0
                || tourIntermission.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("tour.intermission-seconds must be between 1 and 30");
        }
    }

    public record Countdowns(int freeze, int round) {
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
                new Countdowns(
                        countdown.getInt("freeze", 5),
                        countdown.getInt("round", 600)),
                config.getInt("rewards.kill", 0),
                config.getInt("rewards.win", 10),
                config.getInt("vs.kills-to-end", 3),
                maps,
                LootTable.fromYaml(chest, 1, 6),
                CombatStyle.from(config),
                Duration.ofSeconds(config.getInt("rematch.window-seconds", 15)),
                SumoConfig.from(config),
                Duration.ofSeconds(config.getInt("tour.intermission-seconds", 3)));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
