package me.blockcraft.zombies.config;

import me.blockcraft.api.game.GameConfig;
import me.blockcraft.zombies.game.EnemyKind;
import me.blockcraft.zombies.game.Perk;
import me.blockcraft.zombies.game.Weapon;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

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

    private static double share(ZombiesConfig config, int wave, EnemyKind kind) {
        var mix = config.mix(wave);
        return mix.get(kind) / mix.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(ZombiesConfigTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }
}
