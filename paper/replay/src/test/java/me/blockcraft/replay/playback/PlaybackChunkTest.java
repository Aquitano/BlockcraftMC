package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackChunkTest {

    @Test
    public void emptyTerrainSkipsWritesButPlacedBlocksMustBeClearedBeforeRewind() {
        var chunk = new PlaybackChunk(new ReplayEvent.Chunk(4, UUID.randomUUID(), -1, -1, -64, 384,
                List.of("minecraft:air"), new int[384 * 256]));
        int position = chunk.index(-1, 64, -1);
        assertFalse(chunk.ready());
        assertFalse(chunk.needsWrite(position, "minecraft:air"));
        assertTrue(chunk.needsWrite(position, "minecraft:stone"));
        chunk.complete();
        assertTrue(chunk.ready());

        chunk.written(position, true);
        chunk.rebuilding();
        assertFalse(chunk.ready());
        assertTrue(chunk.needsWrite(position, "minecraft:air"));
        assertEquals(position, chunk.nextOccupied(0));
        chunk.written(position, false);
        assertEquals(-1, chunk.nextOccupied(0));
        assertFalse(chunk.needsWrite(position, "minecraft:air"));
        assertEquals(-1, chunk.index(-1, 320, -1));
    }
}
