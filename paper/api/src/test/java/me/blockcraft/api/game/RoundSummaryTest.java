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

    @Test
    public void aWinnerOutBeforeTheEndBooksNoLoss() {
        var teammate = new RoundSummary.Score("Steve");
        teammate.eliminated = true;

        assertEquals(new Stats(0, 0, 1, 0, 0), teammate.stats(true, true));
    }

    @Test
    public void heartsReadLikeTheHealthBarWithADecimalComma() {
        assertEquals("2,5", RoundSummary.hearts(5.0));
        assertEquals("3", RoundSummary.hearts(6.0));
        assertEquals("2,5", RoundSummary.hearts(4.2));
        assertEquals("0,5", RoundSummary.hearts(0.3));
    }
}
