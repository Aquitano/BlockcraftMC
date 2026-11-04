package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotSightTest {

    @Test
    public void acquiresInFrontWithoutSeeingNewEnemiesBehindOrLosingElevatedTargets() {
        var facing = new Vector(0, 0, 1);
        assertTrue(BotSight.sees(facing, new Vector(1, 8, 2), 120));
        assertFalse(BotSight.sees(facing, new Vector(1, 0, -2), 120));
        assertFalse(BotSight.sees(facing, new Vector(3, 0, 0), 120));
    }
}
