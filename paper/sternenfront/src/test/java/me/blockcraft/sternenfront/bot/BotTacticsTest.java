package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotTacticsTest {

    @Test
    public void siegesOnlyHoldingAPostWithAnEnemyInRangeAndFitToStay() {
        assertTrue(BotTactics.sieges(true, true, false, false));
        assertFalse(BotTactics.sieges(false, true, false, false));
        assertFalse(BotTactics.sieges(true, false, false, false));
        assertFalse(BotTactics.sieges(true, true, true, false));
        assertFalse(BotTactics.sieges(true, true, false, true));
    }

    @Test
    public void aGuardContestsItsPostUnlessHurtOrVentingWithCoverAtHand() {
        assertTrue(BotTactics.contests(true, false, false));
        assertTrue(BotTactics.contests(false, true, true));
        assertFalse(BotTactics.contests(true, true, false));
        assertFalse(BotTactics.contests(true, false, true));
    }

    @Test
    public void throwsOnlyAtAnEnemyDugInOutsideItsOwnBlast() {
        assertTrue(BotTactics.throwsAt(true, 12, 6));
        assertFalse(BotTactics.throwsAt(false, 12, 6));
        assertFalse(BotTactics.throwsAt(true, 6.5, 6));
        assertFalse(BotTactics.throwsAt(true, 40, 6));
    }

    @Test
    public void raisesAWallOnlyHoldingOpenGroundAgainstANearEnemy() {
        assertTrue(BotTactics.raisesWall(true, false, 12));
        assertFalse(BotTactics.raisesWall(false, false, 12));
        assertFalse(BotTactics.raisesWall(true, true, 12));
        assertFalse(BotTactics.raisesWall(true, false, 3));
        assertFalse(BotTactics.raisesWall(true, false, 30));
    }

    @Test
    public void lobsFartherTargetsHigherAlongTheirDirection() {
        var near = BotTactics.lob(new Vector(8, 0, 0), 0.9);
        var far = BotTactics.lob(new Vector(0, 0, 16), 0.9);
        assertEquals(1, near.length(), 0.001);
        assertEquals(0, far.getX(), 0.001);
        assertTrue(far.getZ() > 0);
        assertTrue(far.getY() > near.getY());
    }
}
