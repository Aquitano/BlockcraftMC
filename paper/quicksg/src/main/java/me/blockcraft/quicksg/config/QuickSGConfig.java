package me.blockcraft.quicksg.config;

import me.blockcraft.api.game.LootTable;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;


public record QuickSGConfig(
        int minPlayers,
        int maxPlayers,
        String globalStatsHead,
        Countdowns countdowns,
        WorldBorderSettings worldBorder,
        Rewards rewards,
        int kitPrice,
        LootTable loot
) {

    public record Countdowns(int lobby, int freeze, int protection, int game, int trackerAfter, int restart,
                             int idleRestart, int idleRestartRetry) {
    }

    public record WorldBorderSettings(boolean enabled, double lobbyRadius, double ingameRadius) {
    }

    public record Rewards(int kill, int death, int win) {
    }

    public static QuickSGConfig from(FileConfiguration config) {
        var countdown = section(config, "countdown");
        var border = section(config, "world-border");
        var rewards = section(config, "rewards");
        var chest = section(config, "chest");
        return new QuickSGConfig(
                config.getInt("min-players", 2),
                config.getInt("max-players", 8),
                config.getString("global-stats-head", "Kevos"),
                new Countdowns(
                        countdown.getInt("lobby", 30),
                        countdown.getInt("freeze", 5),
                        countdown.getInt("protection", 20),
                        countdown.getInt("game", 1200),
                        countdown.getInt("tracker-after", 300),
                        countdown.getInt("restart", 10),
                        countdown.getInt("idle-restart", 3000),
                        countdown.getInt("idle-restart-retry", 2000)),
                new WorldBorderSettings(
                        border.getBoolean("enabled", false),
                        border.getDouble("lobby-radius", 30),
                        border.getDouble("ingame-radius", 60)),
                new Rewards(rewards.getInt("kill", 5), rewards.getInt("death", 2), rewards.getInt("win", 15)),
                config.getInt("kit-price", 200),
                LootTable.fromYaml(chest, 1, 7));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
