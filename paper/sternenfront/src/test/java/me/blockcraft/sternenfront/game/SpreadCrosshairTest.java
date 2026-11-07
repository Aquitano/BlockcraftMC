package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.config.SternenfrontConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SpreadCrosshairTest {

    private static final YamlConfiguration CONFIG = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
    private static final WeaponHandling HANDLING = WeaponHandling.from(CONFIG.getConfigurationSection("combat.handling"));
    private static final SpreadCrosshair CROSSHAIR = new SpreadCrosshair(CONFIG.getDoubleList("combat-feedback.spread-steps"));

    private record Stance(String name, boolean moving, boolean crouched, double heat, int size) { }

    private static final Stance[] STANCES = {
            new Stance("still and cold", false, false, 0, 0),
            new Stance("crouched", false, true, 0, 0),
            new Stance("crouched and hot", false, true, 1, 0),
            new Stance("still and warm", false, false, 0.4, 1),
            new Stance("walking and cold", true, false, 0, 2),
            new Stance("still and hot", false, false, 1, 2),
            new Stance("walking and warm", true, false, 0.5, 3),
            new Stance("walking and hot", true, false, 1, 4),
    };

    @Test
    public void everyBlasterWithSpreadClosesAtRestAndOpensFullyHotOnTheMove() {
        var config = SternenfrontConfig.from(CONFIG);
        for (var blaster : Blaster.values()) {
            if (blaster == Blaster.SPEEDER_CANNON) {
                continue;
            }
            double base = config.stats(blaster).spread();
            for (var stance : STANCES) {
                var size = CROSSHAIR.size(base, HANDLING.spread(base, stance.moving(), stance.crouched(), stance.heat()));
                assertEquals(blaster + " " + stance.name(), blaster.precision() ? Optional.empty() : Optional.of(stance.size()), size);
            }
        }
    }

    @Test
    public void extraThresholdsBeyondThePacksSizesAreDropped() {
        var crosshair = new SpreadCrosshair(List.of(3.0, 1.0, 2.0, 4.0, 5.0, 6.0));
        assertEquals(Optional.of(SpreadCrosshair.SIZES - 1), crosshair.size(1, 10));
        assertEquals(Optional.of(1), crosshair.size(1, 1.5));
    }
}
