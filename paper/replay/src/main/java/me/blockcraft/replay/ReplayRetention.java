package me.blockcraft.replay;

import me.blockcraft.replay.storage.ReplayStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ReplayRetention {

    private record Archive(UUID id, Path path, long bytes, Instant modified) { }

    private ReplayRetention() { }

    static void clean(ReplayStore store, ReplayRepository repository, ReplayConfig config,
                      Set<UUID> active, Instant now, Logger logger) {
        if (!Files.isDirectory(config.directory(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var archives = new ArrayList<Archive>();
        try (var files = Files.newDirectoryStream(config.directory(), "*.bcr")) {
            for (Path path : files) {
                var name = path.getFileName().toString();
                if (!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.bcr")
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                archives.add(new Archive(UUID.fromString(name.substring(0, 36)), path, Files.size(path),
                        Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant()));
            }
        } catch (IOException error) {
            logger.log(Level.WARNING, "Could not inspect replay retention", error);
            return;
        }
        archives.sort(Comparator.comparing(Archive::modified));
        long total = archives.stream().mapToLong(Archive::bytes).sum();
        int recoveries = 0;
        for (var archive : archives) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                if (!active.contains(archive.id()) && (archive.modified().isBefore(now.minus(config.retention()))
                        || total > config.maxStoredBytes())) {
                    repository.remove(archive.id());
                    if (Files.deleteIfExists(archive.path())) {
                        total -= archive.bytes();
                    }
                    Files.deleteIfExists(config.directory().resolve(archive.id() + ".ready"));
                } else if (recoveries < 4 && store.isReady(archive.id())
                        && repository.accessible(archive.id(), new UUID(0, 0), true).isEmpty()) {
                    recoveries++;
                    var saved = store.inspect(archive.id());
                    repository.save(saved);
                }
            } catch (IOException | RuntimeException error) {
                logger.log(Level.WARNING, "Could not maintain replay " + archive.id(), error);
            }
        }
        try (var files = Files.newDirectoryStream(config.directory(), "*.part")) {
            var expired = now.minusSeconds(config.limits().maxDurationTicks() / 20L + 3600);
            for (Path path : files) {
                if (path.getFileName().toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.part")
                        && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(expired)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException error) {
            logger.log(Level.WARNING, "Could not remove expired incomplete replays", error);
        }
    }
}
