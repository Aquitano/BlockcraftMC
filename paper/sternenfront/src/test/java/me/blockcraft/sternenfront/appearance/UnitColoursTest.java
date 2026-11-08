package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class UnitColoursTest {

    @Test
    public void eachSidesFireteamsWearTheirOwnColourAndAThirdStartsOver() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var units = UnitColours.from(YamlConfiguration.loadConfiguration(reader).getConfigurationSection("appearance"));
            assertEquals(0x30487A, units.colourOf(Faction.KOLONIEN, 0));
            assertEquals(0xC07818, units.colourOf(Faction.KOLONIEN, 1));
            assertEquals(units.colourOf(Faction.KOLONIEN, 0), units.colourOf(Faction.KOLONIEN, 2));
            assertNotEquals(units.colourOf(Faction.HEGEMONIE, 0), units.colourOf(Faction.HEGEMONIE, 1));
        }
    }

    @Test
    public void aSideWithoutValidColoursStaysUnmarked() {
        var config = new YamlConfiguration();
        config.set("unit-colours.kolonien", List.of("not a colour"));
        var units = UnitColours.from(config);
        assertEquals(0xFFFFFF, units.colourOf(Faction.KOLONIEN, 0));
        assertEquals(0xFFFFFF, units.colourOf(Faction.HEGEMONIE, 3));
    }
}
