package me.blockcraft.ttt.task.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LedgerGameTest {

    @Test
    public void dealsExactlyTheWantedWrongSumsEachOffByOneToThree() {
        for (int round = 0; round < 500; round++) {
            var page = LedgerGame.deal(9, 3);
            assertEquals(9, page.size());
            var wrong = page.stream().filter(LedgerGame.Entry::wrong).toList();
            assertEquals(3, wrong.size());
            for (var entry : wrong) {
                int off = Math.abs(entry.shown() - entry.actual());
                assertTrue(off >= 1 && off <= 3);
                assertTrue(entry.shown() >= 0);
            }
            for (var entry : page) {
                assertTrue(entry.actual() >= 1 && Math.max(entry.left(), entry.shown()) < 33);
            }
        }
    }
}
