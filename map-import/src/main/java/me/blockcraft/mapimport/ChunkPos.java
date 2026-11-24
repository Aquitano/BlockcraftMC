package me.blockcraft.mapimport;

public record ChunkPos(int x, int z) {

    /** The region file {@code r.<x>.<z>.mca} this chunk is stored in. */
    public ChunkPos region() {
        return new ChunkPos(x >> 5, z >> 5);
    }

    /** The slot of this chunk in its region file's header. */
    public int index() {
        return (x & 31) + (z & 31) * 32;
    }

    public static ChunkPos inRegion(ChunkPos region, int index) {
        return new ChunkPos(region.x * 32 + index % 32, region.z * 32 + index / 32);
    }

    @Override
    public String toString() {
        return x + " " + z;
    }

    public static ChunkPos parse(String line) {
        var parts = line.trim().split(" ");
        return new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
