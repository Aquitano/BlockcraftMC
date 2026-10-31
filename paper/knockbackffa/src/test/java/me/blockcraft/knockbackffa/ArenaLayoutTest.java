package me.blockcraft.knockbackffa;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ArenaLayoutTest {
    @Test
    public void everyLayoutSupportsAllSpawnsAndPickupsAndRotatesWithoutRepeating() {
        for (var layout : ArenaLayout.values()) {
            assertTrue(layout.floor(18, 0));
            assertTrue(layout.floor(-18, 0));
            assertTrue(layout.floor(0, 18));
            assertTrue(layout.floor(0, -18));
            int offset = layout.pickupOffset();
            for (int x : new int[]{-offset, offset}) {
                for (int z : new int[]{-offset, offset}) assertTrue(layout.floor(x, z));
            }
        }
        assertEquals(ArenaLayout.ISLANDS, ArenaLayout.ISLANDS.next().next().next());
    }
}
