package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RallyTest {

    @Test
    public void aLeaderLeftAloneInAFightWaitsUntilSomeoneComesUp() {
        var rally = new Rally();
        assertFalse(rally.update(true, false, false, 0, 400));
        assertTrue(rally.update(true, true, false, 20, 400));
        assertTrue(rally.update(true, false, false, 200, 400));
        assertFalse(rally.update(false, false, false, 220, 400));
    }

    @Test
    public void itWaitsOnceForAtMostItsTimeAndNeverInsideThePost() {
        var rally = new Rally();
        assertTrue(rally.update(true, true, false, 0, 400));
        assertFalse(rally.update(true, true, false, 400, 400));
        assertFalse(rally.update(true, true, false, 600, 400));
        assertFalse(new Rally().update(true, true, true, 0, 400));
    }
}
