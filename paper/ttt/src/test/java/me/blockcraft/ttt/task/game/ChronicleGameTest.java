package me.blockcraft.ttt.task.game;

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChronicleGameTest {

    @Test
    public void swapsNeededCountsCycles() {
        assertEquals(0, ChronicleGame.swapsNeeded(new int[]{1, 2, 3, 4, 5, 6, 7}));
        assertEquals(1, ChronicleGame.swapsNeeded(new int[]{2, 1, 3, 4, 5, 6, 7}));
        assertEquals(6, ChronicleGame.swapsNeeded(new int[]{2, 3, 4, 5, 6, 7, 1}));
    }

    @Test
    public void dealNeedsAtLeastThreeSwaps() {
        for (int round = 0; round < 500; round++) {
            assertTrue(ChronicleGame.swapsNeeded(ChronicleGame.deal(ThreadLocalRandom.current())) >= 3);
        }
    }
}
