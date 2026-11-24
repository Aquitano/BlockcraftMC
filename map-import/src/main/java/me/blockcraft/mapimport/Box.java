package me.blockcraft.mapimport;

import java.util.Arrays;
import java.util.List;

/** A box of block columns, written {@code x1,z1,x2,z2} with the corners in either order. */
public record Box(int minX, int minZ, int maxX, int maxZ) {

    public static Box parse(String text) {
        int[] corners = Arrays.stream(text.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        if (corners.length != 4) {
            throw new IllegalArgumentException("A box is x1,z1,x2,z2 in blocks, not " + text);
        }
        return new Box(Math.min(corners[0], corners[2]), Math.min(corners[1], corners[3]),
                Math.max(corners[0], corners[2]), Math.max(corners[1], corners[3]));
    }

    /** Boxes separated by semicolons. */
    public static List<Box> parseAll(String text) {
        return Arrays.stream(text.split(";")).filter(box -> !box.isBlank()).map(Box::parse).toList();
    }

    /** Whether the chunk reaches into this box widened by {@code margin} whole chunks. */
    public boolean reaches(ChunkPos chunk, int margin) {
        return chunk.x() >= (minX >> 4) - margin && chunk.x() <= (maxX >> 4) + margin
                && chunk.z() >= (minZ >> 4) - margin && chunk.z() <= (maxZ >> 4) + margin;
    }

    public static Box around(List<ChunkPos> chunks) {
        return new Box(chunks.stream().mapToInt(ChunkPos::x).min().orElseThrow() * 16,
                chunks.stream().mapToInt(ChunkPos::z).min().orElseThrow() * 16,
                chunks.stream().mapToInt(ChunkPos::x).max().orElseThrow() * 16 + 15,
                chunks.stream().mapToInt(ChunkPos::z).max().orElseThrow() * 16 + 15);
    }

    @Override
    public String toString() {
        return minX + "," + minZ + "," + maxX + "," + maxZ;
    }
}
