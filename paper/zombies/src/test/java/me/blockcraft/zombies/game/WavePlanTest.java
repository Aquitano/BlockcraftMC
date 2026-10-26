package me.blockcraft.zombies.game;

import me.blockcraft.zombies.config.ZombiesConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class WavePlanTest {

    private final ZombiesConfig.Waves waves = new ZombiesConfig.Waves(30, 6, 2, 0.6, 0.07, 20, 0.5, 7, 32, 10,
            new ZombiesConfig.Late(10, 0.1, 0.05, 0.5), new ZombiesConfig.Hounds(5, 10, 0.5), 3, 0.3);

    @Test
    public void squadSizeScalesEnemyCountAndOnlyLateHealth() {
        var solo = WavePlan.forSquad(1, 1, waves);
        var squad = WavePlan.forSquad(1, 4, waves);
        assertEquals(6, solo.enemies());
        assertEquals(17, squad.enemies());
        assertEquals(solo.healthMultiplier(), squad.healthMultiplier(), 0);
        assertEquals(squad, WavePlan.forSquad(1, 20, waves));
        assertEquals(2.48, WavePlan.forSquad(15, 1, waves).healthMultiplier(), 0.001);
        assertEquals(2.73, WavePlan.forSquad(15, 2, waves).healthMultiplier(), 0.001);
    }

    @Test
    public void healthAndSpawnPaceGrowFasterAfterTheLateWaveWhileSpawnDelayStaysBounded() {
        assertEquals(1.63, WavePlan.forSquad(10, 1, waves).healthMultiplier(), 0.001);
        assertEquals(16, WavePlan.forSquad(10, 1, waves).spawnTicks());
        assertEquals(11, WavePlan.forSquad(15, 1, waves).spawnTicks());
        var boss = WavePlan.forSquad(30, 1, waves);
        assertTrue(boss.boss());
        assertEquals(64, boss.enemies());
        assertEquals(7, boss.spawnTicks());
        assertEquals(5.03, boss.healthMultiplier(), 0.001);
    }

    @Test
    public void houndWavesComeEveryTenthWaveFromTheFirstWithHalfTheCountButNeverOnBossWaves() {
        assertFalse(WavePlan.forSquad(4, 1, waves).hounds());
        var hounds = WavePlan.forSquad(5, 1, waves);
        assertTrue(hounds.hounds());
        assertEquals(7, hounds.enemies());
        assertTrue(WavePlan.forSquad(15, 2, waves).hounds());
        assertFalse(WavePlan.forSquad(10, 1, waves).hounds());
        var everyFive = new ZombiesConfig.Waves(30, 6, 2, 0.6, 0.07, 20, 0.5, 7, 32, 10,
                waves.late(), new ZombiesConfig.Hounds(5, 5, 0.5), 3, 0.3);
        assertFalse(WavePlan.forSquad(10, 1, everyFive).hounds());
    }
}
