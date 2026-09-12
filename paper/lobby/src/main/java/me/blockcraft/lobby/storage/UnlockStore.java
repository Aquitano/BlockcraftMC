package me.blockcraft.lobby.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.UUID;

/** Per-player unlock flags stored as {@code <uuid>.<key>: true} in a YAML file. */
public final class UnlockStore {

    private final Path file;
    private final YamlConfiguration yaml;

    public UnlockStore(Path file) {
        this.file = file;
        this.yaml = YamlConfiguration.loadConfiguration(file.toFile());
    }

    public boolean has(UUID player, String key) {
        return yaml.getBoolean(player + "." + key);
    }

    public void unlock(UUID player, String key) {
        yaml.set(player + "." + key, true);
        save();
    }

    public void record(UUID player, String key, String value) {
        yaml.set(player + "." + key, value);
        save();
    }

    private void save() {
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
