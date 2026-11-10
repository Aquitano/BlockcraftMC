package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChargesTest {

    @Test
    public void spentChargesComeBackOneAtATime() {
        var grenades = new Charges(2, 300);

        assertTrue(grenades.use(0));
        assertTrue(grenades.use(10));
        assertFalse(grenades.use(20));
        assertEquals(280, grenades.ticksToNext(20));

        assertEquals(1, grenades.stored(300));
        assertEquals(300, grenades.ticksToNext(300));
        assertEquals(2, grenades.stored(600));
        assertEquals(0, grenades.ticksToNext(600));
    }

    @Test
    public void aChargeSpentWhileAnotherRefillsWaitsItsTurn() {
        var grenades = new Charges(2, 300);
        grenades.use(0);
        grenades.use(200);

        assertEquals(1, grenades.stored(300));
        assertTrue(grenades.use(300));
        assertEquals(0, grenades.stored(599));
        assertEquals(1, grenades.stored(600));
    }
}
