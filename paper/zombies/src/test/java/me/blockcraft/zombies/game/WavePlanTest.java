package me.blockcraft.zombies.game;

import me.blockcraft.zombies.config.ZombiesConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class WavePlanTest {

    private final ZombiesConfig.Waves waves = new ZombiesConfig.Waves(30, 6, 2, 0.6, 0.07, 32, 8, 32, 10);

    @Test
    public void squadSizeScalesEnemyCountWithoutIncreasingIndividualHealth() {
        var solo = WavePlan.forSquad(1, 1, waves);
        var squad = WavePlan.forSquad(1, 4, waves);
        assertEquals(6, solo.enemies());
        assertEquals(17, squad.enemies());
        assertEquals(solo.healthMultiplier(), squad.healthMultiplier(), 0);
        assertEquals(squad, WavePlan.forSquad(1, 20, waves));
    }

    @Test
    public void laterWavesAddHealthAndBossesWhileSpawnDelayStaysBounded() {
        assertFalse(WavePlan.forSquad(9, 1, waves).boss());
        var boss = WavePlan.forSquad(30, 1, waves);
        assertTrue(boss.boss());
        assertEquals(64, boss.enemies());
        assertEquals(8, boss.spawnTicks());
        assertEquals(3.03, boss.healthMultiplier(), 0.001);
    }
}
