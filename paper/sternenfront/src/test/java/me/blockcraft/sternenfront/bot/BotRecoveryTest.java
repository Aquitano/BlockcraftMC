package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotRecoveryTest {

    @Test
    public void sustainedHazardsExpireWhileReturningOrDeployingStartsAFreshGrace() {
        var recovery = new BotRecovery();
        assertFalse(recovery.expired(true, 100, 40));
        assertFalse(recovery.expired(true, 139, 40));
        assertTrue(recovery.expired(true, 140, 40));
        recovery.clear();
        assertFalse(recovery.expired(true, 200, 40));
        assertFalse(recovery.expired(false, 220, 40));
        assertFalse(recovery.expired(true, 230, 40));
        assertFalse(recovery.expired(true, 269, 40));
        assertTrue(recovery.expired(true, 270, 40));
    }
}
