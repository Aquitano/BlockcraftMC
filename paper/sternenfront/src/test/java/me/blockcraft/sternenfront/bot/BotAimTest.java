package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BotAimTest {

    @Test
    public void walkingStateDoesNotFlipAtTheOldSingleSpeedThreshold() {
        assertTrue(BotAim.moving(false, new Vector(0, 0, 0.07)));
        assertTrue(BotAim.moving(true, new Vector(0, 0, 0.04)));
        assertFalse(BotAim.moving(false, new Vector(0, 0, 0.04)));
        assertFalse(BotAim.moving(true, new Vector(0, 0, 0.01)));
    }

    @Test
    public void aimScattersMoreJustAfterPickingATargetAndSettles() {
        assertEquals(2.5, BotAim.settle(0, 30, 2.5), 0.001);
        assertEquals(1.75, BotAim.settle(15, 30, 2.5), 0.001);
        assertEquals(1, BotAim.settle(45, 30, 2.5), 0.001);
        assertEquals(1, BotAim.settle(0, 0, 2.5), 0.001);
    }

    @Test
    public void turnsGraduallyEvenWhenTheEnemyMovesDirectlyBehind() {
        var start = new Vector(0, 0, 1);
        var turned = BotAim.turn(start, new Vector(0, 0, -1), 12);
        assertEquals(12, Math.toDegrees(start.angle(turned)), 0.001);
        assertEquals(1, turned.length(), 0.001);
        assertTrue(turned.getZ() > 0);
        assertEquals(new Vector(0, 0, 1), BotAim.turn(new Vector(0.01, 0, 1), start, 12));
    }
}
