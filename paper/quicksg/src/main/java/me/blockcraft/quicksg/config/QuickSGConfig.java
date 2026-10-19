package me.blockcraft.quicksg.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;


/** QuickSG's own keys; the ones every game shares are {@link me.blockcraft.api.game.GameConfig}'s. */
public record QuickSGConfig(
        int maxPlayers,
        Countdowns countdowns,
        WorldBorderSettings worldBorder,
        Tracker tracker,
        Rewards rewards,
        int kitPrice,
        LootTable loot,
        CombatStyle combat
) {

    public record Countdowns(int freeze, int protection, int round, int trackerAfter) {
    }

    public record WorldBorderSettings(boolean enabled, double lobbyRadius, double ingameRadius) {
    }

    public record Tracker(double rangeHorizontal, double rangeVertical, int cooldownTicks) {
    }

    public record Rewards(int kill, int death, int win) {
    }

    public static QuickSGConfig from(FileConfiguration config) {
        var countdown = section(config, "countdown");
        var border = section(config, "world-border");
        var rewards = section(config, "rewards");
        var chest = section(config, "chest");
        return new QuickSGConfig(
                config.getInt("players.max", 8),
                new Countdowns(
                        countdown.getInt("freeze", 5),
                        countdown.getInt("protection", 20),
                        countdown.getInt("round", 1200),
                        countdown.getInt("tracker-after", 300)),
                new WorldBorderSettings(
                        border.getBoolean("enabled", false),
                        border.getDouble("lobby-radius", 30),
                        border.getDouble("ingame-radius", 60)),
                new Tracker(config.getDouble("tracker.range-horizontal", 100.0), config.getDouble("tracker.range-vertical", 25.0),
                        Math.max(0, config.getInt("tracker.cooldown-ticks", 20))),
                new Rewards(rewards.getInt("kill", 5), rewards.getInt("death", 2), rewards.getInt("win", 15)),
                config.getInt("kit-price", 200),
                LootTable.fromYaml(chest, 1, 7),
                CombatStyle.from(config));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
