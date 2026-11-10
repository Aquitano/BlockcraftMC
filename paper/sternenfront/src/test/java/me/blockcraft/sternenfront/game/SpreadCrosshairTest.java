package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.config.SternenfrontConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;

public class SpreadCrosshairTest {

    private static final YamlConfiguration CONFIG = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
    private static final WeaponHandling HANDLING = WeaponHandling.from(CONFIG.getConfigurationSection("combat.handling"));
    private static final SpreadCrosshair CROSSHAIR = new SpreadCrosshair(CONFIG.getDoubleList("combat-feedback.spread-steps"));

    private record Case(String name, WeaponHandling.Posture posture, double heat, int size) { }

    private static final WeaponHandling.Posture STILL = new WeaponHandling.Posture(false, false, false, OptionalInt.empty());
    private static final WeaponHandling.Posture AIMING = new WeaponHandling.Posture(false, true, false, OptionalInt.empty());
    private static final Case[] CASES = {
            new Case("still and cold", STILL, 0, 1),
            new Case("aiming", AIMING, 0, 0),
            new Case("aiming and hot", AIMING, 1, 0),
            new Case("walking and hot", new WeaponHandling.Posture(true, false, false, OptionalInt.empty()), 1, SpreadCrosshair.SIZES - 1),
            new Case("jumping and hot", new WeaponHandling.Posture(false, false, true, OptionalInt.empty()), 1, SpreadCrosshair.SIZES - 1),
    };

    @Test
    public void everyBlasterClosesAtTheSightsAndOpensFullyHotOnTheMove() {
        var config = SternenfrontConfig.from(CONFIG);
        for (var blaster : Blaster.values()) {
            if (blaster == Blaster.SPEEDER_CANNON) {
                continue;
            }
            double base = config.stats(blaster).spread();
            for (var each : CASES) {
                var size = CROSSHAIR.size(base, HANDLING.spread(blaster, base, each.posture(), each.heat()));
                assertEquals(blaster + " " + each.name(), Optional.of(each.size()), size);
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
