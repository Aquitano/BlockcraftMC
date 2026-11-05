package me.blockcraft.sternenfront.config;

import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SternenfrontConfigTest {

    @Test
    public void keepsBundledBurstSettingsWhenOnlyDamageIsOverridden() {
        var config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        config.set("blasters.repeater.damage", 3);
        var repeater = SternenfrontConfig.from(config).stats(Blaster.REPEATER);
        assertEquals(3, repeater.damage(), 0.001);
        assertEquals(3, repeater.bolts());
        assertEquals(2, repeater.burstTicks());
    }

    @Test
    public void factionWeaponsInheritTheirProfileAndKeepPartialOverrides() {
        var config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        config.set("blasters.repeater.damage", 3);
        config.set("blasters.wrist-blaster.heat", 12);
        var parsed = SternenfrontConfig.from(config);
        var wrist = parsed.stats(Blaster.WRIST_BLASTER);
        assertEquals(3, wrist.damage(), 0.001);
        assertEquals(12, wrist.heat(), 0.001);
        assertEquals(parsed.stats(Blaster.REPEATER).bolts(), wrist.bolts());
        assertEquals(parsed.stats(Blaster.REPEATER).burstTicks(), wrist.burstTicks());
        assertEquals(parsed.stats(Blaster.LONG_RIFLE), parsed.stats(Blaster.E5S));
        assertEquals(parsed.stats(Blaster.TWIN_PISTOL), parsed.stats(Blaster.WESTAR_PISTOLS));
    }

    @Test
    public void readsEachFactionsClassBodies() {
        var config = SternenfrontConfig.from(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile()));
        assertEquals(new SternenfrontConfig.ClassStats(36, 0.88, 1.1), config.stats(Faction.HEGEMONIE, CombatClass.HEAVY));
        assertEquals(new SternenfrontConfig.ClassStats(30, 1, 1), config.stats(Faction.KOLONIEN, CombatClass.HEAVY));
        assertEquals(18, config.stats(Faction.HEGEMONIE, CombatClass.MEDIC).health(), 0.001);
        assertEquals(20, config.stats(Faction.KOLONIEN, CombatClass.MEDIC).health(), 0.001);
    }

    @Test
    public void siegeModeFiresTheRepeaterFasterAndCoolerAtTheSameDamage() {
        var config = SternenfrontConfig.from(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile()));
        var normal = config.stats(Blaster.REPEATER);
        var sieged = config.siegeMode().apply(normal);
        assertEquals(normal.heat() / 2, sieged.heat(), 0.001);
        assertEquals(normal.damage(), sieged.damage(), 0.001);
        assertTrue(sieged.bolts() * 1.0 / sieged.cooldownTicks() > normal.bolts() * 1.0 / normal.cooldownTicks());
    }
}
