package me.blockcraft.sternenfront.config;

import me.blockcraft.sternenfront.game.Blaster;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

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
}
