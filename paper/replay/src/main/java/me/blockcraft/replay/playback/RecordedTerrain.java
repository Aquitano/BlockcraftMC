package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates a replay world from the chunks of one recorded world, so the server builds the map on its chunk workers
 * and sends every chunk once, finished, instead of the playback writing it block by block on the main thread. Chunks
 * the recording lacks stay empty; one biome keeps grass and water the same colour across the map.
 */
final class RecordedTerrain extends ChunkGenerator {

    private static final BiomeProvider PLAINS = new BiomeProvider() {
        @Override
        public Biome getBiome(WorldInfo world, int x, int y, int z) {
            return Biome.PLAINS;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo world) {
            return List.of(Biome.PLAINS);
        }
    };

    private final Map<Long, ReplayEvent.Chunk> chunks;
    private final Map<String, BlockData> blocks;

    RecordedTerrain(Map<Long, ReplayEvent.Chunk> chunks, Map<String, BlockData> blocks) {
        this.chunks = Map.copyOf(chunks);
        this.blocks = blocks;
    }

    static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    Collection<ReplayEvent.Chunk> chunks() {
        return chunks.values();
    }

    @Override
    public void generateNoise(WorldInfo world, Random random, int chunkX, int chunkZ, ChunkData data) {
        var chunk = chunks.get(key(chunkX, chunkZ));
        if (chunk == null) {
            return;
        }
        var palette = chunk.palette().stream().map(blocks::get)
                .map(block -> block.getMaterial().isAir() ? null : block).toArray(BlockData[]::new);
        int[] states = chunk.blocks();
        for (int index = 0; index < states.length; index++) {
            var block = palette[states[index]];
            if (block != null) {
                data.setBlock(index & 15, chunk.minY() + (index >> 8), index >> 4 & 15, block);
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo world) {
        return PLAINS;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 64, 0.5);
    }
}
