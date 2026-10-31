package me.blockcraft.mapimport;

import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.util.ArrayList;
import java.util.List;

/** Which chunks of the finished world the template keeps; everything else the generator makes anew. */
public sealed interface Mode {

    String FLAT_GENERATOR = "minecraft:flat";

    boolean keeps(Chunk chunk, boolean hasEntities);

    /** What the template's generator makes where no chunk is stored. */
    String outside();

    /** Drops chunks that hold exactly the superflat ground the generator makes, in its biome, and nothing else. */
    record Flat(List<String> ground, String biome) implements Mode {

        public static Flat of(CompoundBinaryTag flatSettings) {
            List<String> ground = new ArrayList<>();
            for (var layer : flatSettings.getList("layers", BinaryTagTypes.COMPOUND)) {
                var compound = (CompoundBinaryTag) layer;
                for (int i = 0; i < compound.getInt("height"); i++) {
                    ground.add(compound.getString("block"));
                }
            }
            return new Flat(List.copyOf(ground), flatSettings.getString("biome"));
        }

        @Override
        public boolean keeps(Chunk chunk, boolean hasEntities) {
            int minY = chunk.minY();
            return hasEntities || chunk.hasBlockEntities()
                    || !chunk.allBlocks((y, block) -> sameBlock(block, groundAt(y - minY)))
                    || !chunk.allBiomes(biome::equals);
        }

        private String groundAt(int layer) {
            return layer < ground.size() ? ground.get(layer) : "minecraft:air";
        }

        private static boolean sameBlock(String block, String ground) {
            return block.equals(ground) || Chunk.AIR.contains(block) && Chunk.AIR.contains(ground);
        }

        @Override
        public String outside() {
            return "the same superflat ground (" + describe() + ")";
        }

        public String describe() {
            if (ground.stream().allMatch(Chunk.AIR::contains)) {
                return "nothing, in " + biome;
            }
            List<String> layers = new ArrayList<>();
            int start = 0;
            for (int i = 1; i <= ground.size(); i++) {
                if (i == ground.size() || !ground.get(i).equals(ground.get(start))) {
                    int height = i - start;
                    layers.add((height == 1 ? "" : height + "x ") + ground.get(start));
                    start = i;
                }
            }
            return String.join(", ", layers) + " from the bottom of the world, in " + biome;
        }
    }

    /** Drops chunks without a single block, block entity or entity. */
    record Void() implements Mode {

        @Override
        public boolean keeps(Chunk chunk, boolean hasEntities) {
            return hasEntities || chunk.hasBlockEntities() || !chunk.allBlocks((y, block) -> Chunk.AIR.contains(block));
        }

        @Override
        public String outside() {
            return "void";
        }
    }

    /** Keeps every chunk the old world stored. */
    record All() implements Mode {

        @Override
        public boolean keeps(Chunk chunk, boolean hasEntities) {
            return true;
        }

        @Override
        public String outside() {
            return "the old generator's terrain, without structures";
        }
    }
}
