package me.blockcraft.knockbackffa;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ArenaLayoutTest {
    @Test
    public void everyLayoutSupportsAllSpawnsAndRotatesWithoutRepeating() {
        for (var layout : ArenaLayout.values()) {
            assertTrue(layout.floor(18, 0));
            assertTrue(layout.floor(-18, 0));
            assertTrue(layout.floor(0, 18));
            assertTrue(layout.floor(0, -18));
        }
        assertEquals(ArenaLayout.ISLANDS, ArenaLayout.ISLANDS.next().next().next());
    }
}
