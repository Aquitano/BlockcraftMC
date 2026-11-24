package me.blockcraft.sternenfront.game;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropshipLandingTest {

    private static DropshipLanding.Settings settings(String landing) {
        var config = new YamlConfiguration();
        try {
            config.loadFromString("presentation:\n  landing: " + landing + "\n");
        } catch (InvalidConfigurationException failure) {
            throw new IllegalArgumentException(failure);
        }
        return DropshipLanding.Settings.from(config);
    }

    @Test
    public void ridersAreSetDownOnlyOnTheGroundWithTheDoorsOpen() {
        var landing = settings("{enabled: true, height: 16, glide: 4, descent-ticks: 30, door-ticks: 20, settle-ticks: 4, lift-ticks: 20, share-ticks: 10}");
        assertEquals(10, landing.doorsOpenAt());
        assertEquals(1.0, landing.descended(30), 0);
        assertEquals(34, landing.releaseAt());
        assertEquals(54, landing.endAt());
        assertEquals(0.0, landing.climbed(landing.releaseAt()), 0);
    }

    @Test
    public void teammatesBoardOnlyWhileTheDoorsAreShut() {
        var landing = settings("{enabled: true, height: 16, glide: 4, descent-ticks: 30, door-ticks: 25, settle-ticks: 4, lift-ticks: 20, share-ticks: 10}");
        assertTrue(landing.boardable(4));
        assertFalse(landing.boardable(5));
    }

    @Test
    public void theDescentFitsOneMoveTheClientDraws() {
        var landing = settings("{enabled: true, height: 16, glide: 4, descent-ticks: 200, door-ticks: 300, settle-ticks: 4, lift-ticks: 20, share-ticks: 10}");
        assertEquals(59, landing.descentTicks());
        assertEquals(59, landing.doorTicks());
    }
}
