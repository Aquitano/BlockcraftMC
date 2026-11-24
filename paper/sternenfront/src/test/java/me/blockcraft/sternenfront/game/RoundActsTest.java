package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class RoundActsTest {

    private static final RoundActs.Rules RULES = new RoundActs.Rules(0.65, 360, 0.30, 780, 2, 3, 0.5, 0.6, 60, 22);

    private static RoundActs acts() {
        return new RoundActs(RULES, 150);
    }

    @Test
    public void ticketsStartAnActAtTheirShareOfTheStart() {
        var acts = acts();

        assertEquals(Optional.empty(), acts.second(98, 1));
        assertEquals(Optional.of(RoundActs.Act.ASSAULT), acts.second(97, 2));
        assertEquals(Optional.empty(), acts.second(46, 3));
        assertEquals(Optional.of(RoundActs.Act.LAST_STAND), acts.second(45, 4));
    }

    @Test
    public void timeStartsAnActWhateverTheTickets() {
        var acts = acts();

        assertEquals(Optional.empty(), acts.second(150, 359));
        assertEquals(Optional.of(RoundActs.Act.ASSAULT), acts.second(150, 360));
        assertEquals(Optional.empty(), acts.second(150, 779));
        assertEquals(Optional.of(RoundActs.Act.LAST_STAND), acts.second(150, 780));
    }

    @Test
    public void collapsingTicketsPassThroughEveryActInOrder() {
        var acts = acts();

        assertEquals(Optional.of(RoundActs.Act.ASSAULT), acts.second(10, 1));
        assertEquals(Optional.of(RoundActs.Act.LAST_STAND), acts.second(10, 2));
    }

    @Test
    public void actsNeverMoveBack() {
        var acts = acts();
        acts.second(10, 1);
        acts.second(10, 2);

        assertEquals(Optional.empty(), acts.second(150, 3));
        assertEquals(RoundActs.Act.LAST_STAND, acts.act());
    }

    @Test
    public void thePriorityPostWeighsMoreInTheAssaultAndEveryPostInTheLastStand() {
        var acts = acts();
        assertEquals(1, acts.bleedWeight(true));

        acts.second(90, 1);
        assertEquals(2, acts.bleedWeight(true));
        assertEquals(1, acts.bleedWeight(false));

        acts.second(10, 2);
        assertEquals(3, acts.bleedWeight(true));
        assertEquals(3, acts.bleedWeight(false));
    }
}
