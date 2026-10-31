package me.blockcraft.archive;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.time.Duration;

public record ArchiveConfig(int maxPlayers, Path exhibits, Duration unloadAfter, int noteMaxLength) {

    public static ArchiveConfig from(ConfigurationSection config) {
        return new ArchiveConfig(
                config.getInt("max-players"),
                Path.of(config.getString("exhibits.directory", "/exhibits")),
                Duration.ofSeconds(config.getLong("exhibits.unload-after-seconds")),
                config.getInt("notes.max-length"));
    }
}
