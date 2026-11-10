package me.blockcraft.sternenfront.bot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VolleyTest {

    @Test
    public void completesAThreeBoltPullBetweenBotDecisionsAndCancelsPendingShots() {
        var volley = new Volley();
        volley.start(100, 3, 2);
        assertTrue(volley.next(100));
        assertFalse(volley.next(100));
        assertFalse(volley.next(101));
        assertTrue(volley.next(102));
        assertTrue(volley.next(104));
        assertFalse(volley.active());
        assertFalse(volley.next(120));
        volley.start(200, 3, 2);
        assertTrue(volley.next(200));
        volley.clear();
        assertFalse(volley.next(202));
    }
}
