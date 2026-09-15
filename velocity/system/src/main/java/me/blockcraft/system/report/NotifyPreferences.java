package me.blockcraft.system.report;

import me.blockcraft.common.config.YamlFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff members who switched report notifications off, persisted as {@code ignored-staff.yml}. */
public final class NotifyPreferences {

    private final Path file;
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();

    public NotifyPreferences(Path file) {
        this.file = file;
        load();
    }

    public boolean isMuted(UUID player) {
        return muted.contains(player);
    }

    public void mute(UUID player) {
        muted.add(player);
        save();
    }

    public void unmute(UUID player) {
        muted.remove(player);
        save();
    }

    private void load() {
        if (Files.notExists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            if (YamlFile.load(in).get("muted") instanceof List<?> ids) {
                ids.forEach(id -> muted.add(UUID.fromString(String.valueOf(id))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + file, e);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new Yaml().dump(Map.of("muted", muted.stream().map(UUID::toString).sorted().toList())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
