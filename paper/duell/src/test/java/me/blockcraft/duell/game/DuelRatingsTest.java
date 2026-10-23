package me.blockcraft.duell.game;

import org.junit.Test;

import static org.junit.Assert.fail;

public class DuelRatingsTest {
    @Test
    public void unratedResultsNeverAccessEloOrScheduleSettlement() {
        var ratings = new DuelRatings(null, null, null);
        ratings.settle(DuelRating.UNRATED, null, null, () -> fail("Unrated results must not settle Elo"));
    }
}
