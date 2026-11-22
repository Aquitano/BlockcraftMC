package me.blockcraft.starwars;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.Assert.*;

public class WeaponSpecTest {
    @Test public void suppliedWeaponsKeepTheirDistinctFiringProfiles() {
        var source = Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"));
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(source, StandardCharsets.UTF_8));
        var weapons = WeaponSpec.load(config.getConfigurationSection("weapons"));
        assertEquals(12, weapons.size());
        assertEquals(WeaponSpec.Mode.BURST, weapons.get("a280").mode());
        assertEquals(WeaponSpec.Mode.BURST, weapons.get("ee3").mode());
        assertEquals(WeaponSpec.Mode.ROCKET, weapons.get("rocket").mode());
        assertEquals(WeaponSpec.Mode.GRENADE, weapons.get("thermal").mode());
        assertEquals(8, weapons.get("ca87").pellets());
        assertTrue(weapons.get("amban").range() > weapons.get("dl44").range());
        assertTrue(weapons.get("e11").cooldown() < weapons.get("dl44").cooldown());
    }
}
