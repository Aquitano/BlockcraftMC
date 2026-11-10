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
    public void firstShotsMissToOneSideUntilTheAimSettles() {
        var aim = new Vector(0, 0, 1);
        var first = BotAim.misaim(aim, 0, 0.15, 0, 30);
        assertEquals(0.15, first.length(), 1e-9);
        assertEquals(0, first.dot(aim), 1e-9);
        assertEquals(0.075, BotAim.misaim(aim, 0, 0.15, 15, 30).length(), 1e-9);
        assertEquals(-1, first.clone().normalize().dot(BotAim.misaim(aim, Math.PI, 0.15, 0, 30).normalize()), 1e-9);
        assertEquals(0, BotAim.misaim(aim, 0, 0.15, 30, 30).length(), 1e-9);
        assertEquals(0, BotAim.misaim(new Vector(0, 1, 0), 1, 0.15, 0, 0).length(), 1e-9);
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

    @Test
    public void firesOnlyAheadAndTurnsRoundForATargetBehind() {
        var behind = new Vector(0, 0, -1);
        assertTrue(BotAim.covers(0, new Vector(0.5, 0.2, 1), 60));
        assertFalse(BotAim.covers(0, behind, 60));
        assertTrue(BotAim.covers(170, new Vector(0.1, 0, -1), 60));
        assertEquals(20, Math.abs(BotAim.turnYaw(0, behind, 20)), 0.001);
        assertEquals(180, Math.abs(BotAim.turnYaw(170, behind, 20)), 0.001);
    }
}
