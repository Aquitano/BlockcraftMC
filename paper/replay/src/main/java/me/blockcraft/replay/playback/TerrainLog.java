package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every recorded block change with the state it replaced, so the world reaches any tick from the one it shows by
 * rewriting only the blocks that differ, in either direction.
 */
final class TerrainLog {

    record Position(UUID world, int x, int y, int z) { }

    record Change(int tick, Position position, String before, String after) { }

    private record ChunkKey(UUID world, int x, int z) { }

    private final List<Change> changes;
    private int applied;

    TerrainLog(List<Change> changes) {
        this.changes = changes;
    }

    /**
     * The changes the recording's block events make to its chunk baselines. A chunk captured late stands for every
     * earlier tick too, and a recorded neighbour that kept its state is no change.
     */
    static List<Change> changes(List<ReplayEvent> events) {
        var chunks = new HashMap<ChunkKey, ReplayEvent.Chunk>();
        for (var event : events) {
            if (event instanceof ReplayEvent.Chunk chunk) {
                chunks.putIfAbsent(new ChunkKey(chunk.world(), chunk.x(), chunk.z()), chunk);
            }
        }
        var current = new HashMap<Position, String>();
        var changes = new ArrayList<Change>();
        for (var event : events) {
            if (!(event instanceof ReplayEvent.Blocks blocks)) {
                continue;
            }
            for (var block : blocks.blocks()) {
                var position = new Position(block.world(), block.x(), block.y(), block.z());
                var before = Optional.ofNullable(current.get(position)).or(() -> baseline(chunks, position));
                if (before.isPresent() && !before.get().equals(block.data())) {
                    changes.add(new Change(blocks.tick(), position, before.get(), block.data()));
                    current.put(position, block.data());
                }
            }
        }
        return List.copyOf(changes);
    }

    private static Optional<String> baseline(Map<ChunkKey, ReplayEvent.Chunk> chunks, Position position) {
        var chunk = chunks.get(new ChunkKey(position.world(), Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16)));
        if (chunk == null || position.y() < chunk.minY() || position.y() >= chunk.minY() + chunk.height()) {
            return Optional.empty();
        }
        int index = (position.y() - chunk.minY()) * 256 + Math.floorMod(position.z(), 16) * 16 + Math.floorMod(position.x(), 16);
        return Optional.of(chunk.palette().get(chunk.blocks()[index]));
    }

    /** The writes that take the world from the tick it shows to {@code tick}, one per position. */
    Map<Position, String> seek(int tick) {
        var writes = new LinkedHashMap<Position, String>();
        while (applied < changes.size() && changes.get(applied).tick() <= tick) {
            var change = changes.get(applied++);
            writes.put(change.position(), change.after());
        }
        while (applied > 0 && changes.get(applied - 1).tick() > tick) {
            var change = changes.get(--applied);
            writes.put(change.position(), change.before());
        }
        return writes;
    }
}
