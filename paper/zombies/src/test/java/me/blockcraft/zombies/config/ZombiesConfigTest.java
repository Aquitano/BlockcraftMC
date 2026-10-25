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
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
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
}
