package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.game.Ability;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeroConfigTest {

    @Test
    public void equipsTheNewPowersWithNamesCooldownsAndDistinctMaterials() {
        var config = HeroConfig.from(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile()));
        var messages = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        assertEquals(List.of(HeroAbility.JETPACK, HeroAbility.WRIST_ROCKET), Hero.BOUNTY_HUNTER.abilities());
        assertEquals(List.of(HeroAbility.SABER_THROW, HeroAbility.FORCE_LIGHTNING), Hero.SITH.abilities());
        assertEquals(320, config.cooldownTicks(HeroAbility.WRIST_ROCKET));
        assertEquals(280, config.cooldownTicks(HeroAbility.FORCE_LIGHTNING));
        assertEquals(new HeroConfig.Rocket(64, 1.6, 3.5, 18), config.rocket());
        assertEquals(new HeroConfig.Lightning(10, 50, 30, 5, 3, 12), config.lightning());
        var materials = new HashSet<Material>();
        for (var ability : Ability.values()) {
            materials.add(ability.material());
        }
        for (var ability : HeroAbility.values()) {
            assertTrue(ability.key(), materials.add(ability.material()));
            assertFalse(ability.key(), messages.getString("hero.ability." + ability.key() + ".name", "").isEmpty());
        }
    }

    @Test
    public void clampsInvalidRocketAndLightningSettingsBeforeTheyReachTheTickLoop() {
        var yaml = new YamlConfiguration();
        yaml.set("heroes.abilities.wrist-rocket.speed", 0);
        yaml.set("heroes.abilities.wrist-rocket.radius", -1);
        yaml.set("heroes.abilities.force-lightning.ticks", 0);
        yaml.set("heroes.abilities.force-lightning.pulse-ticks", 0);
        yaml.set("heroes.abilities.force-lightning.targets", 0);
        yaml.set("heroes.abilities.force-lightning.damage", -1);
        var config = HeroConfig.from(yaml);
        assertEquals(0.1, config.rocket().speed(), 0.001);
        assertEquals(0.1, config.rocket().radius(), 0.001);
        assertEquals(1, config.lightning().ticks());
        assertEquals(1, config.lightning().pulseTicks());
        assertEquals(1, config.lightning().targets());
        assertEquals(0, config.lightning().damagePerPulse(), 0.001);
    }
}
