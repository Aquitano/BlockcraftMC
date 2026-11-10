package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModeTest {

    private static final int AIR = 0;
    private static final int BEDROCK = 1;
    private static final int DIRT = 2;
    private static final int GRASS = 3;
    private static final int GOLD = 4;

    /** The 1.8 default superflat after Paper's upgrade: air up to y 0, then bedrock, two dirt and grass. */
    private final Mode.Flat flat = Mode.Flat.of(CompoundBinaryTag.builder()
            .put("layers", ListBinaryTag.from(List.of(layer("minecraft:air", 64), layer("minecraft:bedrock", 1),
                    layer("minecraft:dirt", 2), layer("minecraft:grass_block", 1))))
            .putString("biome", "minecraft:plains")
            .build());

    @Test
    public void flatDropsOnlyThePlainGround() {
        assertFalse(flat.keeps(chunk(ground(), "minecraft:plains", false), false));

        int[] dug = ground();
        dug[3 * 256 + 5 * 16 + 5] = GOLD;
        assertTrue(flat.keeps(chunk(dug, "minecraft:plains", false), false));
        assertTrue(flat.keeps(chunk(ground(), "minecraft:desert", false), false));
        assertTrue(flat.keeps(chunk(ground(), "minecraft:plains", true), false));
        assertTrue(flat.keeps(chunk(ground(), "minecraft:plains", false), true));
    }

    @Test
    public void voidKeepsEveryChunkWithABlock() {
        var empty = new int[4096];
        assertFalse(new Mode.Void().keeps(chunk(empty, "minecraft:the_void", false), false));

        int[] pillar = new int[4096];
        pillar[7 * 256] = BEDROCK;
        assertTrue(new Mode.Void().keeps(chunk(pillar, "minecraft:the_void", false), false));
        assertTrue(new Mode.Void().keeps(chunk(empty, "minecraft:the_void", false), true));
    }

    private static CompoundBinaryTag layer(String block, int height) {
        return CompoundBinaryTag.builder().putString("block", block).putInt("height", height).build();
    }

    /** Section 0 of the plain ground, as palette indices in y, z, x order. */
    private static int[] ground() {
        int[] blocks = new int[4096];
        for (int i = 0; i < 256; i++) {
            blocks[i] = BEDROCK;
            blocks[256 + i] = DIRT;
            blocks[512 + i] = DIRT;
            blocks[768 + i] = GRASS;
        }
        return blocks;
    }

    /** A finished chunk from y -64 to 31 with air below section 0, the given section 0 and air above it. */
    private static Chunk chunk(int[] section0, String biome, boolean blockEntity) {
        var air = CompoundBinaryTag.builder().put("palette", ListBinaryTag.from(List.of(StringBinaryTag.stringBinaryTag("minecraft:air")))).build();
        var palette = ListBinaryTag.from(List.<BinaryTag>of(
                CompoundBinaryTag.builder().putString("", "minecraft:air").build(),
                CompoundBinaryTag.builder().putString("", "minecraft:bedrock").build(),
                CompoundBinaryTag.builder().putString("Name", "minecraft:dirt").build(),
                CompoundBinaryTag.builder().putString("id", "minecraft:grass_block")
                        .put("properties", CompoundBinaryTag.builder().putString("snowy", "false").build()).build(),
                CompoundBinaryTag.builder().putString("", "minecraft:gold_block").build()));
        var blocks = CompoundBinaryTag.builder().put("palette", palette).putLongArray("data", pack(section0)).build();
        var biomes = CompoundBinaryTag.builder().put("palette", ListBinaryTag.from(List.of(StringBinaryTag.stringBinaryTag(biome)))).build();
        var sections = ListBinaryTag.builder();
        for (int y = -4; y <= 1; y++) {
            sections.add(CompoundBinaryTag.builder().putByte("Y", (byte) y).put("block_states", y == 0 ? blocks : air).put("biomes", biomes).build());
        }
        var blockEntities = blockEntity
                ? ListBinaryTag.from(List.of(CompoundBinaryTag.builder().putString("id", "minecraft:sign").build()))
                : ListBinaryTag.empty();
        return new Chunk(CompoundBinaryTag.builder()
                .putString("Status", "minecraft:full").putInt("yPos", -4)
                .put("sections", sections.build()).put("block_entities", blockEntities)
                .build());
    }

    /** Four bits per block, sixteen to a long, as 26.3 packs a palette of up to sixteen entries. */
    private static long[] pack(int[] indices) {
        long[] data = new long[indices.length / 16];
        for (int i = 0; i < indices.length; i++) {
            data[i / 16] |= (long) indices[i] << (i % 16 * 4);
        }
        return data;
    }
}
