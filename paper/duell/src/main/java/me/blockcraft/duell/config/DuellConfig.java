package me.blockcraft.duell.config;

import me.blockcraft.duell.game.DuelMode;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.api.game.ThrowFight;
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
        int vsBestOf,
        Map<DuelMode, List<String>> maps,
        LootTable loot,
        CombatStyle combat,
        Duration rematchWindow,
        SumoConfig sumo,
        ThrowFight.Settings throwFight,
        Duration tourIntermission,
        boolean ratedEnabled
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

    public static DuellConfig from(FileConfiguration yaml) {
        var config = BundledDefaults.strict(yaml);
        var countdown = config.section("countdown");
        var maps = new EnumMap<DuelMode, List<String>>(DuelMode.class);
        for (var mode : DuelMode.values()) {
            maps.put(mode, List.copyOf(yaml.getStringList("maps." + mode.configKey())));
        }
        return new DuellConfig(
                new Countdowns(
                        countdown.integer("freeze"),
                        countdown.integer("round")),
                config.integer("rewards.kill"),
                config.integer("rewards.win"),
                config.integer("vs.best-of"),
                maps,
                LootTable.fromYaml(section(yaml, "sg-chest")),
                CombatStyle.from(yaml),
                Duration.ofSeconds(config.integer("rematch.window-seconds")),
                SumoConfig.from(config.section("sumo")),
                ThrowFight.Settings.from(config.section("throw-fight")),
                Duration.ofSeconds(config.integer("tour.intermission-seconds")),
                config.flag("rating.enabled"));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
