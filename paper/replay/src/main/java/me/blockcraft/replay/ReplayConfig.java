package me.blockcraft.replay;

import me.blockcraft.replay.data.ReplayLimits;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.time.Duration;

record ReplayConfig(String playbackGroup, Path directory, int historyLimit, long maxStoredBytes,
                    long minFreeBytes, Duration retention, Duration cleanupInterval, ReplayLimits limits) {

    private static final long MIB = 1024L * 1024;

    static ReplayConfig load(FileConfiguration config, Path dataDirectory) {
        String configured = System.getenv("BLOCKCRAFT_REPLAY_DIRECTORY");
        Path directory = configured == null || configured.isBlank()
                ? dataDirectory.resolve(config.getString("storage.directory", "recordings")) : Path.of(configured);
        var limits = new ReplayLimits(
                number(config, "recording.sample-ticks", 1, 20),
                number(config, "recording.max-duration-minutes", 1, 60) * 1200,
                number(config, "recording.max-participants", 1, 64),
                number(config, "recording.max-entities", 0, 256),
                number(config, "recording.max-chunks", 1, 256),
                number(config, "recording.capture-radius", 0, 2),
                number(config, "recording.chunks-per-tick", 1, 2),
                number(config, "recording.max-block-changes-per-tick", 1, 4096),
                number(config, "recording.queue-capacity", 2, 64),
                number(config, "recording.max-events", 100, 200000),
                number(config, "recording.max-archive-mib", 1, 128) * MIB,
                number(config, "recording.max-decoded-mib", 1, 256) * MIB,
                number(config, "playback.max-viewers", 1, 4),
                number(config, "playback.blocks-per-tick", 256, 16384));
        return new ReplayConfig(config.getString("playback-group", "replay"), directory.toAbsolutePath().normalize(),
                number(config, "history-limit", 1, 200),
                number(config, "storage.max-total-mib", 128, 1048576) * MIB,
                number(config, "storage.min-free-mib", 64, 1048576) * MIB,
                Duration.ofDays(number(config, "storage.retention-days", 1, 365)),
                Duration.ofMinutes(number(config, "storage.cleanup-minutes", 1, 1440)), limits);
    }

    private static int number(FileConfiguration config, String key, int min, int max) {
        int value = config.getInt(key);
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}
