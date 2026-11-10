package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BoundingTest {

    private static final boolean[] BOTH = {true, true};

    @Test
    public void theLeadersElementCoversWhileTheOtherBoundsFirst() {
        var bounding = new Bounding();
        assertEquals(1, bounding.update(BOTH, false, 0, 80));
    }

    @Test
    public void elementsSwapOnlyOnceTheMoversArriveOrRunOutOfTime() {
        var bounding = new Bounding();
        bounding.update(BOTH, false, 0, 80);
        assertEquals(1, bounding.update(BOTH, false, 40, 80));
        assertEquals(0, bounding.update(BOTH, true, 50, 80));
        assertEquals(0, bounding.update(BOTH, false, 120, 80));
        assertEquals(1, bounding.update(BOTH, false, 130, 80));
        assertEquals(3, bounding.bound());
    }

    @Test
    public void aLoneElementHoldsBetweenBounds() {
        var bounding = new Bounding();
        boolean[] lone = {true, false};
        assertEquals(0, bounding.update(lone, false, 0, 80));
        assertEquals(Bounding.NONE, bounding.update(lone, true, 20, 80));
        assertEquals(Bounding.NONE, bounding.update(lone, false, 50, 80));
        assertEquals(0, bounding.update(lone, false, 60, 80));
    }

    @Test
    public void whenTheMoversFallTheCoveringElementMoves() {
        var bounding = new Bounding();
        bounding.update(BOTH, false, 0, 80);
        assertEquals(0, bounding.update(new boolean[] {true, false}, false, 20, 80));
    }
}
