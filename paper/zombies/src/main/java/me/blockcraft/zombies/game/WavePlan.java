package me.blockcraft.zombies.game;

import me.blockcraft.zombies.config.ZombiesConfig;

public record WavePlan(int wave, int enemies, int spawnTicks, double healthMultiplier, boolean boss, boolean hounds) {

    public static WavePlan forSquad(int wave, int players, ZombiesConfig.Waves config) {
        int extraPlayers = Math.clamp(players, 1, 4) - 1;
        int lateWaves = Math.max(0, wave - config.late().from());
        boolean boss = wave % config.bossEvery() == 0;
        var hounds = config.hounds();
        boolean houndWave = !boss && wave >= hounds.first() && (wave - hounds.first()) % hounds.every() == 0;
        double horde = Math.ceil((config.baseEnemies() + (wave - 1) * config.enemiesPerWave()) * (1 + extraPlayers * config.extraPlayer()));
        int enemies = (int) Math.ceil(houndWave ? horde * hounds.share() : horde);
        int spawnTicks = Math.max(config.minimumSpawnTicks(), (int) Math.round(config.spawnTicks()
                - (wave - 1) * config.spawnTicksPerWave() - lateWaves * config.late().spawnTicksPerWave()));
        double health = 1 + (wave - 1) * config.healthPerWave()
                + lateWaves * (config.late().healthPerWave() + extraPlayers * config.late().healthPerExtraPlayer());
        return new WavePlan(wave, enemies, spawnTicks, health, boss, houndWave);
    }
}
