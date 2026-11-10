package me.blockcraft.sternenfront.game;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WeaponHandlingTest {

    private static final WeaponHandling.Posture STILL = new WeaponHandling.Posture(false, false, false, OptionalInt.empty());

    private static WeaponHandling handling() throws InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.loadFromString("""
                moving: 2
                aiming: 0.5
                settle-ticks: 10
                airborne: 1.5
                hot: 1
                sprint-recovery-ticks: 5
                falloff-from: 0.5
                falloff-minimum: 0.6
                blasters:
                  pistol: {moving: 1.2, sprint-recovery-ticks: 3}
                """);
        return WeaponHandling.from(config);
    }

    @Test
    public void movingAirborneAndHeatOpenTheAimAndAimingTightensIt() throws InvalidConfigurationException {
        var handling = handling();
        assertEquals(0.01, handling.spread(Blaster.CARBINE, 0.01, STILL, 0), 1e-9);
        assertEquals(0.02, handling.spread(Blaster.CARBINE, 0.01, new WeaponHandling.Posture(true, false, false, OptionalInt.empty()), 0), 1e-9);
        assertEquals(0.005, handling.spread(Blaster.CARBINE, 0.01, new WeaponHandling.Posture(false, true, false, OptionalInt.empty()), 0), 1e-9);
        assertEquals(0.015, handling.spread(Blaster.CARBINE, 0.01, new WeaponHandling.Posture(false, false, true, OptionalInt.empty()), 0), 1e-9);
        assertEquals(0.02, handling.spread(Blaster.CARBINE, 0.01, STILL, 1), 1e-9);
    }

    @Test
    public void aScopeSettlesTheSpreadToNothingOverItsSettleTicks() throws InvalidConfigurationException {
        var handling = handling();
        assertEquals(0.01, handling.spread(Blaster.LONG_RIFLE, 0.01, new WeaponHandling.Posture(false, false, false, OptionalInt.of(0)), 0), 1e-9);
        assertEquals(0.004, handling.spread(Blaster.LONG_RIFLE, 0.01, new WeaponHandling.Posture(false, false, false, OptionalInt.of(6)), 0), 1e-9);
        assertEquals(0, handling.spread(Blaster.LONG_RIFLE, 0.01, new WeaponHandling.Posture(true, false, false, OptionalInt.of(10)), 0), 1e-9);
    }

    @Test
    public void aBlasterOverridesOnlyWhatItNamesAndASeparatistOneTakesItsProfiles() throws InvalidConfigurationException {
        var handling = handling();
        var moving = new WeaponHandling.Posture(true, true, false, OptionalInt.empty());
        assertEquals(0.006, handling.spread(Blaster.PISTOL, 0.01, moving, 0), 1e-9);
        assertEquals(0.006, handling.spread(Blaster.SE14, 0.01, moving, 0), 1e-9);
        assertEquals(0.01, handling.spread(Blaster.E5, 0.01, moving, 0), 1e-9);
    }

    @Test
    public void aSprintKeepsTheGunDownForItsBlastersRecovery() throws InvalidConfigurationException {
        var handling = handling();
        assertTrue(handling.lowered(Blaster.CARBINE, 10, 15));
        assertFalse(handling.lowered(Blaster.CARBINE, 10, 16));
        assertTrue(handling.lowered(Blaster.PISTOL, 10, 13));
        assertFalse(handling.lowered(Blaster.PISTOL, 10, 14));
    }

    @Test
    public void damageHoldsUpToTheFalloffStartThenThinsToTheMinimum() throws InvalidConfigurationException {
        var handling = handling();
        assertEquals(10, handling.damage(10, 40, 100), 1e-9);
        assertEquals(8, handling.damage(10, 75, 100), 1e-9);
        assertEquals(6, handling.damage(10, 100, 100), 1e-9);
    }
}
