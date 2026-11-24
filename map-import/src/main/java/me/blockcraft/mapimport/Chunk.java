package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** A chunk as Paper 26.3 saves it once it is finished: sections of paletted block states and biomes. */
public record Chunk(CompoundBinaryTag tag) {

    public static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private static final int SECTION_BLOCKS = 4096;
    private static final int SECTION_BIOMES = 64;

    /** What a block expects at a height: the name a block at {@code y} must have. */
    @FunctionalInterface
    public interface Expected {
        boolean matches(int y, String block);
    }

    public String status() {
        return tag.getString("Status");
    }

    public int minY() {
        return tag.getInt("yPos") * 16;
    }

    public boolean hasBlockEntities() {
        return !tag.getList("block_entities").isEmpty();
    }

    /**
     * Whether every block matches; a section the chunk does not store holds air. 26.3 writes palette entries as plain
     * names, as {@code {Name, Properties}}, as {@code {id, properties}}, and wraps names in {@code {"": ...}} where a
     * palette mixes them; block names alone decide, properties do not.
     */
    public boolean allBlocks(Expected expected) {
        Map<Integer, CompoundBinaryTag> sections = sections();
        int lowest = tag.getInt("yPos");
        int highest = sections.keySet().stream().max(Integer::compare).orElse(lowest - 1);
        for (int y = lowest; y <= highest; y++) {
            var states = sections.getOrDefault(y, CompoundBinaryTag.empty()).getCompound("block_states");
            var palette = names(states.getList("palette"));
            if (palette.isEmpty()) {
                palette = List.of("minecraft:air");
            }
            boolean[][] fits = new boolean[16][palette.size()];
            for (int layer = 0; layer < 16; layer++) {
                for (int entry = 0; entry < palette.size(); entry++) {
                    fits[layer][entry] = expected.matches(y * 16 + layer, palette.get(entry));
                }
            }
            int[] indices = unpack(states.getLongArray("data"), palette.size(), SECTION_BLOCKS, 4);
            for (int i = 0; i < SECTION_BLOCKS; i++) {
                if (!fits[i >> 8][indices[i]]) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean allBiomes(Predicate<String> expected) {
        for (var section : sections().values()) {
            var biomes = section.getCompound("biomes");
            var palette = names(biomes.getList("palette"));
            int[] indices = unpack(biomes.getLongArray("data"), palette.size(), SECTION_BIOMES, 1);
            for (int index : indices) {
                if (!palette.isEmpty() && !expected.test(palette.get(index))) {
                    return false;
                }
            }
        }
        return true;
    }

    private Map<Integer, CompoundBinaryTag> sections() {
        Map<Integer, CompoundBinaryTag> sections = new HashMap<>();
        for (var section : tag.getList("sections", BinaryTagTypes.COMPOUND)) {
            var compound = (CompoundBinaryTag) section;
            sections.put((int) compound.getByte("Y"), compound);
        }
        return sections;
    }

    private static List<String> names(ListBinaryTag palette) {
        return palette.stream().map(Chunk::name).toList();
    }

    public static String name(BinaryTag entry) {
        return switch (entry) {
            case StringBinaryTag name -> name.value();
            case CompoundBinaryTag state when state.contains("Name") -> state.getString("Name");
            case CompoundBinaryTag state when state.contains("id") -> state.getString("id");
            case CompoundBinaryTag wrapped when wrapped.contains("") -> name(wrapped.get(""));
            default -> throw new IllegalArgumentException("Unknown palette entry " + entry);
        };
    }

    /** Palette indices packed into longs; since 1.16 an index never spans two longs. */
    static int[] unpack(long[] data, int paletteSize, int count, int minBits) {
        int[] indices = new int[count];
        if (paletteSize <= 1 || data.length == 0) {
            return indices;
        }
        int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            indices[i] = (int) (data[i / perLong] >>> (i % perLong * bits) & mask);
        }
        return indices;
    }
}
