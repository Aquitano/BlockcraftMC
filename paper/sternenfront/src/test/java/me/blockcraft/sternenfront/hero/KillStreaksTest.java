package me.blockcraft.sternenfront.hero;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KillStreaksTest {

    @Test
    public void theRewardComesOncePerLifeAtExactlyTheStreak() {
        var streaks = new KillStreaks(3, 1);
        var player = UUID.randomUUID();

        assertFalse(streaks.kill(player));
        assertFalse(streaks.kill(player));
        assertTrue(streaks.kill(player));
        assertFalse(streaks.kill(player));

        streaks.death(player);
        streaks.kill(player);
        streaks.kill(player);
        assertTrue(streaks.kill(player));
    }

    @Test
    public void botsFillTheStreakMoreSlowlyAndKeepItsOneReward() {
        var streaks = new KillStreaks(2, 3);
        var player = UUID.randomUUID();

        assertFalse(streaks.botKill(player));
        assertFalse(streaks.botKill(player));
        assertFalse(streaks.kill(player));
        assertTrue(streaks.botKill(player));
        assertFalse(streaks.kill(player));
    }
}
