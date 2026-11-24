package me.blockcraft.endergames.game;

import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SwapPairsTest {

    @Test
    public void aSwapNeedsTwoPlayers() {
        assertTrue(SwapPairs.pick(List.of("alone"), new Random(1)).isEmpty());
    }

    @Test
    public void bothSidesAreDifferentPlayers() {
        var random = new Random(7);
        for (int i = 0; i < 200; i++) {
            var pair = SwapPairs.pick(List.of("a", "b", "c"), random).orElseThrow();
            assertNotEquals(pair.first(), pair.second());
        }
    }
}
