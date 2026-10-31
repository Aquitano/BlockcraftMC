package me.blockcraft.replay.data;

public record ReplayLimits(int sampleTicks, int maxDurationTicks, int maxParticipants, int maxEntities, int maxChunks,
                           int captureRadius, int chunksPerTick, int maxBlockChangesPerTick,
                           int queueCapacity, int maxEvents, long maxArchiveBytes, long maxDecodedBytes,
                           int maxPlaybacks, int blocksPerTick) {

    /** How many decoded objects a recording may hold; samples, palette entries and block changes count one each. */
    public long objectBudget() {
        return Math.min(1_000_000, maxDecodedBytes / 64);
    }
}
