package me.blockcraft.duell.config;

import org.bukkit.configuration.ConfigurationSection;

public record SumoConfig(int radius, int floorY, int loseY, double spawnDistance, int pointsToWin,
                         int resetSeconds) {

    public static final int MAX_RADIUS = 16;
    public static final int MIN_FLOOR_Y = 0;
    public static final int MAX_FLOOR_Y = 200;

    public SumoConfig {
        if (radius < 2 || radius > MAX_RADIUS || floorY < MIN_FLOOR_Y || floorY > MAX_FLOOR_Y
                || loseY < -60 || loseY >= floorY) {
            throw new IllegalArgumentException("Invalid Sumo arena bounds or loss threshold");
        }
        if (!Double.isFinite(spawnDistance) || spawnDistance < 1 || spawnDistance > radius - 1) {
            throw new IllegalArgumentException("Sumo spawn-distance must be between 1 and radius - 1");
        }
        if (pointsToWin < 1 || pointsToWin > 100 || resetSeconds < 1 || resetSeconds > 30) {
            throw new IllegalArgumentException("Invalid Sumo scoring or reset settings");
        }
    }

    public static SumoConfig from(ConfigurationSection config) {
        return new SumoConfig(config.getInt("sumo.radius", 4), config.getInt("sumo.floor-y", 64),
                config.getInt("sumo.lose-y", 61), config.getDouble("sumo.spawn-distance", 2),
                config.getInt("sumo.points-to-win", 3), config.getInt("sumo.reset-seconds", 3));
    }
}
