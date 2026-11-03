package me.blockcraft.api.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class BundledDefaultsTest {

    @Test
    public void readsWithAFallbackSeeTheBundledValueUnlessTheOperatorOverrodeIt() throws Exception {
        var bundled = new YamlConfiguration();
        bundled.loadFromString("players:\n  min: 4\n  max: 12\nshop: [knife, vest]\n");
        var overrides = new YamlConfiguration();
        overrides.loadFromString("players:\n  max: 8\nshop: [vest]\n");
        overrides.setDefaults(bundled);

        BundledDefaults.fill(overrides);

        assertEquals(4, overrides.getInt("players.min", 2));
        assertEquals(8, overrides.getInt("players.max", 2));
        assertEquals(List.of("vest"), overrides.getStringList("shop"));
    }
}
