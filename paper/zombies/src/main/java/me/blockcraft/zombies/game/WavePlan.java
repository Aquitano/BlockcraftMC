package me.blockcraft.zombies.game;

import me.blockcraft.zombies.config.ZombiesConfig;

public record WavePlan(int wave, int enemies, int spawnTicks, double healthMultiplier, boolean boss) {

    public static WavePlan forSquad(int wave, int players, ZombiesConfig.Waves config) {
        int squad = Math.clamp(players, 1, 4);
        double scale = 1 + (squad - 1) * config.extraPlayer();
        int enemies = (int) Math.ceil((config.baseEnemies() + (wave - 1) * config.enemiesPerWave()) * scale);
        int spawnTicks = Math.max(config.minimumSpawnTicks(), config.spawnTicks() - wave + 1);
        return new WavePlan(wave, enemies, spawnTicks,
                1 + (wave - 1) * config.healthPerWave(), wave % config.bossEvery() == 0);
    }
}
