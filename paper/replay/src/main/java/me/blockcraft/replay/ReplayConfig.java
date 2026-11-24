package me.blockcraft.replay;

import me.blockcraft.replay.data.ReplayLimits;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.common.config.StrictConfig;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.time.Duration;

record ReplayConfig(String playbackGroup, Path directory, int historyLimit, long maxStoredBytes,
                    long minFreeBytes, Duration retention, Duration cleanupInterval, ReplayLimits limits) {

    private static final long MIB = 1024L * 1024;

    static ReplayConfig load(FileConfiguration config, Path dataDirectory) {
        var strict = BundledDefaults.strict(config);
        String configured = System.getenv("BLOCKCRAFT_REPLAY_DIRECTORY");
        Path directory = configured == null || configured.isBlank()
                ? dataDirectory.resolve(strict.text("storage.directory")) : Path.of(configured);
        var limits = new ReplayLimits(
                number(strict, "recording.sample-ticks", 1, 20),
                number(strict, "recording.max-duration-minutes", 1, 60) * 1200,
                number(strict, "recording.max-participants", 1, 64),
                number(strict, "recording.max-entities", 0, 256),
                number(strict, "recording.max-chunks", 1, 256),
                number(strict, "recording.capture-radius", 0, 2),
                number(strict, "recording.chunks-per-tick", 1, 2),
                number(strict, "recording.max-block-changes-per-tick", 1, 4096),
                number(strict, "recording.queue-capacity", 2, 256),
                number(strict, "recording.max-events", 100, 200000),
                number(strict, "recording.max-archive-mib", 1, 128) * MIB,
                number(strict, "recording.max-decoded-mib", 1, 256) * MIB,
                number(strict, "playback.max-viewers", 1, 4),
                number(strict, "playback.blocks-per-tick", 256, 16384));
        return new ReplayConfig(strict.text("playback-group"), directory.toAbsolutePath().normalize(),
                number(strict, "history-limit", 1, 200),
                number(strict, "storage.max-total-mib", 128, 1048576) * MIB,
                number(strict, "storage.min-free-mib", 64, 1048576) * MIB,
                Duration.ofDays(number(strict, "storage.retention-days", 1, 365)),
                Duration.ofMinutes(number(strict, "storage.cleanup-minutes", 1, 1440)), limits);
    }

    private static int number(StrictConfig config, String key, int min, int max) {
        int value = config.integer(key);
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}
