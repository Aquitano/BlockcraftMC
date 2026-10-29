package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;

import java.util.BitSet;

final class PlaybackChunk {

    private final ReplayEvent.Chunk baseline;
    private final BitSet occupied = new BitSet();
    private boolean ready;

    PlaybackChunk(ReplayEvent.Chunk baseline) {
        this.baseline = baseline;
    }

    ReplayEvent.Chunk baseline() {
        return baseline;
    }

    boolean ready() { return ready; }

    void complete() { ready = true; }

    void rebuilding() { ready = false; }

    boolean needsWrite(int index, String data) {
        return occupied.get(index) || switch (data) {
            case "minecraft:air", "minecraft:cave_air", "minecraft:void_air" -> false;
            default -> true;
        };
    }

    void written(int index, boolean nonAir) {
        occupied.set(index, nonAir);
    }

    int nextOccupied(int from) {
        return occupied.nextSetBit(from);
    }

    int index(int x, int y, int z) {
        if (y < baseline.minY() || y >= baseline.minY() + baseline.height()) {
            return -1;
        }
        return (y - baseline.minY()) * 256 + Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16);
    }
}
