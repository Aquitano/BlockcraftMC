package me.blockcraft.duell.config;

import me.blockcraft.common.config.StrictConfig;

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

    public static SumoConfig from(StrictConfig sumo) {
        return new SumoConfig(sumo.integer("radius"), sumo.integer("floor-y"), sumo.integer("lose-y"),
                sumo.decimal("spawn-distance"), sumo.integer("points-to-win"), sumo.integer("reset-seconds"));
    }
}
