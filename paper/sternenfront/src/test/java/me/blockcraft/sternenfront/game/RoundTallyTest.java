package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.game.RoundTally.Deed;
import org.junit.Test;

import java.util.List;
import java.util.Random;
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
    public void anOrderCompletesAtItsCountAndTheNextBegins() {
        var kills = new Orders.Order(Deed.KILL, 2);
        var captures = new Orders.Order(Deed.CAPTURE, 1);
        var orders = new Orders(List.of(kills, captures), 150, 3, new Random(1));
        var first = orders.of(ann).orElseThrow().order();
        var second = first.equals(kills) ? captures : kills;
        var other = first.deed() == Deed.KILL ? Deed.CAPTURE : Deed.KILL;

        assertTrue(orders.did(ann, other).isEmpty());
        for (int done = 1; done < first.count(); done++) {
            assertTrue(orders.did(ann, first.deed()).isEmpty());
        }
        assertEquals(first, orders.did(ann, first.deed()).orElseThrow());
        assertEquals(second, orders.of(ann).orElseThrow().order());
        assertEquals(0, orders.of(ann).orElseThrow().done());
    }

    @Test
    public void onlyTheDeedsAFighterCanDoMakeOrders() {
        assertEquals(new Orders.Order(Deed.HEADSHOT, 2), Orders.Order.parse(" headshot : 2 ").orElseThrow());
        assertTrue(Orders.Order.parse("kill").isEmpty());
        assertTrue(Orders.Order.parse("kill:many").isEmpty());
        assertTrue(Orders.Order.parse("nap:3").isEmpty());
        assertTrue(Orders.Order.parse("order:1").isEmpty());
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
