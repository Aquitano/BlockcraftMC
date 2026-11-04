package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeavyTacticsTest {

    @Test
    public void siegesOnlyHoldingAPostWithAnEnemyInRangeAndFitToStay() {
        assertTrue(HeavyTactics.sieges(true, true, false, false));
        assertFalse(HeavyTactics.sieges(false, true, false, false));
        assertFalse(HeavyTactics.sieges(true, false, false, false));
        assertFalse(HeavyTactics.sieges(true, true, true, false));
        assertFalse(HeavyTactics.sieges(true, true, false, true));
    }

    @Test
    public void throwsTheChargeOnlyAtAPostOutsideItsOwnBlast() {
        assertTrue(HeavyTactics.throwsCharge(true, 12, 6));
        assertFalse(HeavyTactics.throwsCharge(false, 12, 6));
        assertFalse(HeavyTactics.throwsCharge(true, 6.5, 6));
        assertFalse(HeavyTactics.throwsCharge(true, 40, 6));
    }

    @Test
    public void lobsFartherTargetsHigherAlongTheirDirection() {
        var near = HeavyTactics.lob(new Vector(8, 0, 0), 0.9);
        var far = HeavyTactics.lob(new Vector(0, 0, 16), 0.9);
        assertEquals(1, near.length(), 0.001);
        assertEquals(0, far.getX(), 0.001);
        assertTrue(far.getZ() > 0);
        assertTrue(far.getY() > near.getY());
    }
}
