package me.blockcraft.api.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RoundSummaryTest {

    @Test
    public void everyoneOutOrBeatenBooksOneLossAndADrawCostsTheStandingNone() {
        var knockedOut = new RoundSummary.Score("Steve");
        knockedOut.eliminated = true;
        knockedOut.coins = 7;
        var standing = new RoundSummary.Score("Alex");

        assertEquals(new Stats(0, 0, 0, 1, 7), knockedOut.stats(false, true));
        assertEquals(new Stats(0, 0, 0, 1, 7), knockedOut.stats(false, false));
        assertEquals(new Stats(0, 0, 0, 1, 0), standing.stats(false, true));
        assertEquals(new Stats(0, 0, 1, 0, 0), standing.stats(true, true));
        assertEquals(Stats.EMPTY, standing.stats(false, false));
    }
}
