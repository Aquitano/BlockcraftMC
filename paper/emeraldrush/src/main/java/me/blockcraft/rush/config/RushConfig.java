package me.blockcraft.rush.config;

import org.bukkit.configuration.file.FileConfiguration;

public record RushConfig(
        String mapName,
        int minPlayers,
        String lobbyServer,
        int emeraldsPerOre,
        int oreRespawnSeconds,
        int lobbyCountdown,
        int searchSeconds,
        int shoppingSeconds,
        int deathmatchSeconds,
        int trackerAfterSeconds,
        int restartSeconds,
        int idleRestartMinutes
) {

    public static RushConfig from(FileConfiguration config) {
        return new RushConfig(
                config.getString("MapName", "MapName"),
                config.getInt("MinPlayers", 2),
                config.getString("lobby-server", "lobby"),
                config.getInt("emeralds-per-ore", 12),
                config.getInt("ore-respawn-seconds", 12),
                config.getInt("countdown.lobby", 30),
                config.getInt("countdown.search", 300),
                config.getInt("countdown.shopping", 90),
                config.getInt("countdown.deathmatch", 1200),
                config.getInt("countdown.deathmatch-tracker-after", 300),
                config.getInt("countdown.restart", 10),
                config.getInt("idle-restart-minutes", 50));
    }
}
