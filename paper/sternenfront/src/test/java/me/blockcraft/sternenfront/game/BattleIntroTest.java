package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BattleIntroTest {
    @Test
    public void focusIsLimitedToTheConfiguredShotAndDoesNotFollowASkip() {
        var window = new BattleIntro.FocusWindow(true, 0.75, 0.95);
        assertFalse(window.active(134, 180, false));
        assertTrue(window.active(135, 180, false));
        assertTrue(window.active(170, 180, false));
        assertFalse(window.active(171, 180, false));
        assertFalse(window.active(140, 180, true));
        assertFalse(new BattleIntro.FocusWindow(false, 0.75, 0.95).active(140, 180, false));
        assertFalse(new BattleIntro.FocusWindow(true, Double.NaN, 0.95).active(140, 180, false));
        assertFalse(new BattleIntro.FocusWindow(true, 0.95, 0.75).active(140, 180, false));
    }
}
