package me.blockcraft.zombies.arena;

import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class BlockBoxTest {

    @Test
    public void invalidOrUnboundedRegionsAreRejectedBeforeTouchingTheWorld() {
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(10, 0, 0, -10, 4, 4));
        assertThrows(IllegalArgumentException.class, () -> new BlockBox(0, 0, 0, 100000, 10, 100000));
    }

    @Test
    public void generatedRegionsMustFitInsideEveryPreflightBoundary() {
        var bounds = new BlockBox(-29, 95, -29, 29, 105, 29);
        assertTrue(bounds.contains(new BlockBox(-29, 95, -29, 29, 105, 29)));
        assertFalse(bounds.contains(new BlockBox(11, 96, -10, 40, 104, 10)));
        assertFalse(bounds.contains(new BlockBox(0, 94, 0, 0, 96, 0)));
        assertFalse(bounds.contains(new BlockBox(0, 96, -30, 0, 96, 0)));
    }
}
