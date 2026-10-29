package me.blockcraft.replay.data;

public record ReplayLimits(int sampleTicks, int maxDurationTicks, int maxParticipants, int maxChunks,
                           int captureRadius, int chunksPerTick, int maxBlockChangesPerTick,
                           int queueCapacity, int maxEvents, long maxArchiveBytes, long maxDecodedBytes,
                           int maxPlaybacks, int blocksPerTick, int blocksScannedPerTick) { }
