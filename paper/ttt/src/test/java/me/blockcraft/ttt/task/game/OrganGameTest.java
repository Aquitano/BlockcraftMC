package me.blockcraft.ttt.task.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class OrganGameTest {

    @Test
    public void tuneHasTheLengthAndNeverRepeatsANoteInARow() {
        for (int run = 0; run < 200; run++) {
            var tune = OrganGame.tune(5, 6);
            assertEquals(5, tune.length);
            for (int i = 0; i < tune.length; i++) {
                assertTrue(tune[i] >= 0 && tune[i] < 6);
                if (i > 0) {
                    assertNotEquals(tune[i - 1], tune[i]);
                }
            }
        }
    }
}
