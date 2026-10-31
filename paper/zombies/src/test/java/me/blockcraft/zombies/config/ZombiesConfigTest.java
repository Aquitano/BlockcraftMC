package me.blockcraft.zombies.config;

import me.blockcraft.api.game.GameConfig;
import me.blockcraft.zombies.game.EnemyKind;
import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.WaveEvent;
import me.blockcraft.zombies.game.WavePlan;
import me.blockcraft.zombies.game.Weapon;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.*;

public class ZombiesConfigTest {

    @Test
    public void bundledRulesSupportSoloAndDefineEveryPurchasableWeaponAndEnemy() {
        var yaml = bundled();
        var config = ZombiesConfig.from(yaml);
        assertEquals(1, GameConfig.from(yaml, 1).minPlayers());
        assertEquals(2, GameConfig.from(yaml).minPlayers());
        assertEquals(4, config.maxPlayers());
        assertEquals(Weapon.values().length, config.guns().size());
        assertEquals(Perk.values().length, config.perks().size());
        assertEquals(EnemyKind.values().length, config.enemies().size());
        assertEquals(WaveEvent.Kind.values().length, config.events().weights().size());
        assertEquals(30, config.waves().total());
        assertTrue(config.bleedTicks() > config.reviveTicks());
    }

    @Test
    public void bundledMixShiftsFromWalkersToSpecialsAndKeepsHoundsToTheirOwnWaves() {
        var config = ZombiesConfig.from(bundled());
        assertEquals(1, share(config, 1, EnemyKind.WALKER), 0);
        assertTrue(share(config, config.waves().total(), EnemyKind.WALKER) < share(config, 10, EnemyKind.WALKER));
        for (int wave = 1; wave <= config.waves().total(); wave++) {
            assertEquals(0, share(config, wave, EnemyKind.HELLHOUND), 0);
            assertFalse(config.mix(wave).containsKey(EnemyKind.INFERNO));
        }
    }

    @Test
    public void eventsStartAtTheirFirstWaveAndSkipBossAndHoundWaves() {
        var config = ZombiesConfig.from(bundled());
        var events = config.events();
        assertTrue(events.choices(WavePlan.forSquad(events.fromWave() - 1, 1, config.waves()), true, 5).isEmpty());
        assertEquals(WaveEvent.Kind.values().length, events.choices(WavePlan.forSquad(events.fromWave(), 1, config.waves()), true, 5).size());
        for (int wave = events.fromWave(); wave <= config.waves().total(); wave++) {
            var plan = WavePlan.forSquad(wave, 1, config.waves());
            assertEquals(plan.boss() || plan.hounds(), events.choices(plan, true, 5).isEmpty());
        }
    }

    @Test
    public void powerOutageNeedsBoughtPowerAndToxicFogASecondOpenRoom() {
        var config = ZombiesConfig.from(bundled());
        var plan = WavePlan.forSquad(config.events().fromWave(), 1, config.waves());
        assertEquals(Set.of(WaveEvent.Kind.BLOOD_MOON, WaveEvent.Kind.BOUNTY), config.events().choices(plan, false, 1).keySet());
        assertTrue(config.events().choices(plan, true, 1).containsKey(WaveEvent.Kind.POWER_OUTAGE));
        assertTrue(config.events().choices(plan, false, 2).containsKey(WaveEvent.Kind.TOXIC_FOG));
    }

    @Test
    public void bundledSpecialsJoinTheMixOneAtATimeFromWaveSix() {
        var config = ZombiesConfig.from(bundled());
        var specials = List.of(EnemyKind.CRAWLER, EnemyKind.SHIELD_BEARER, EnemyKind.FROST, EnemyKind.ARSONIST,
                EnemyKind.ELECTRIC, EnemyKind.BREEDER, EnemyKind.SHADOW);
        int previous = 4;
        for (var kind : specials) {
            int first = config.enemies().get(kind).firstWave();
            assertTrue(kind + " joins at " + first, first >= previous + 2 && first <= 24);
            assertEquals(0, config.mix(first - 1).get(kind), 0);
            assertTrue(share(config, first, kind) > 0);
            previous = first;
        }
    }

    private static double share(ZombiesConfig config, int wave, EnemyKind kind) {
        var mix = config.mix(wave);
        return mix.get(kind) / mix.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(ZombiesConfigTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }
}
