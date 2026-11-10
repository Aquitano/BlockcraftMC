package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.game.RoundTally.Deed;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoundTallyTest {

    private final UUID ann = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();

    @Test
    public void awardsNeedRivalsAndEnoughOfTheDeed() {
        var tally = new RoundTally();
        tally.earned(ann, 500);
        assertTrue(tally.awards().isEmpty());

        tally.earned(ben, 300);
        tally.did(ben, Deed.CAPTURE);
        tally.did(ben, Deed.CAPTURE);
        tally.did(ann, Deed.CAPTURE);
        tally.did(ann, Deed.HEADSHOT);

        assertEquals(List.of(new RoundTally.Award("mvp", ann, 500), new RoundTally.Award("conqueror", ben, 2)), tally.awards());
    }

    @Test
    public void aBonusFollowsWhatTheKillPaysUpToItself() {
        assertEquals(6, RoundTally.bonus(25, 25, 100));
        assertEquals(25, RoundTally.bonus(25, 100, 100));
        assertEquals(25, RoundTally.bonus(25, 25, 0));
    }

    @Test
    public void aRankIsTheHighestReached() {
        var ranks = List.of(0L, 100L, 300L);
        assertEquals(0, Career.rankOf(99, ranks));
        assertEquals(1, Career.rankOf(100, ranks));
        assertEquals(2, Career.rankOf(5000, ranks));
    }
}
