package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChunkTest {

    @Test
    public void readsEveryPaletteEntryForm() {
        var properties = CompoundBinaryTag.builder().putString("persistent", "true").build();

        assertEquals("minecraft:stone", Chunk.name(StringBinaryTag.stringBinaryTag("minecraft:stone")));
        assertEquals("minecraft:air", Chunk.name(CompoundBinaryTag.builder().putString("", "minecraft:air").build()));
        assertEquals("minecraft:oak_leaves", Chunk.name(CompoundBinaryTag.builder()
                .putString("id", "minecraft:oak_leaves").put("properties", properties).build()));
        assertEquals("minecraft:grass_block", Chunk.name(CompoundBinaryTag.builder()
                .putString("Name", "minecraft:grass_block").put("Properties", properties).build()));
    }
}
