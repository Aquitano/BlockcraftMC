package me.blockcraft.replay.data;

import java.util.List;
import java.util.UUID;

public sealed interface ReplayEvent {

    int tick();

    record Actors(int tick, List<Actor> actors) implements ReplayEvent {
        public Actors {
            actors = List.copyOf(actors);
        }
    }

    record Actor(UUID id, UUID world, double x, double y, double z, float yaw, float pitch,
                 String pose, List<byte[]> equipment) { }

    record Chunk(int tick, UUID world, int x, int z, int minY, int height,
                 List<String> palette, int[] blocks) implements ReplayEvent { }

    record Blocks(int tick, List<Block> blocks) implements ReplayEvent {
        public Blocks {
            blocks = List.copyOf(blocks);
        }
    }

    record Block(UUID world, int x, int y, int z, String data) { }
}
