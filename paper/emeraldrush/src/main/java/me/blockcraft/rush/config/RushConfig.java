package me.blockcraft.rush.config;

import me.blockcraft.api.game.CombatStyle;
import org.bukkit.configuration.file.FileConfiguration;

public record RushConfig(
        int minPlayers,
        int emeraldsPerOre,
        int oreRespawnSeconds,
        int lobbyCountdown,
        int searchSeconds,
        int shoppingSeconds,
        int deathmatchSeconds,
        int trackerAfterSeconds,
        int restartSeconds,
        int idleRestartMinutes,
        int lives,
        int killCoins,
        int winCoins,
        CombatStyle combat
) {

    public static RushConfig from(FileConfiguration config) {
        return new RushConfig(
                config.getInt("min-players", 2),
                config.getInt("emeralds-per-ore", 12),
                config.getInt("ore-respawn-seconds", 12),
                config.getInt("countdown.lobby", 30),
                config.getInt("countdown.search", 300),
                config.getInt("countdown.shopping", 90),
                config.getInt("countdown.deathmatch", 1200),
                config.getInt("countdown.deathmatch-tracker-after", 300),
                config.getInt("countdown.restart", 10),
                config.getInt("idle-restart-minutes", 50),
                config.getInt("lives", 3),
                config.getInt("rewards.kill", 5),
                config.getInt("rewards.win", 15),
                CombatStyle.from(config));
    }
}
