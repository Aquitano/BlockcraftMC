package me.blockcraft.mapimport;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoxTest {

    @Test
    public void takesCornersInAnyOrderAndWidensByWholeChunks() {
        var box = Box.parse("40, -20, -10, 5");

        assertTrue(box.reaches(new ChunkPos(-1, -2), 0));
        assertTrue(box.reaches(new ChunkPos(2, 0), 0));
        assertFalse(box.reaches(new ChunkPos(3, 0), 0));
        assertTrue(box.reaches(new ChunkPos(3, 0), 1));
    }
}
