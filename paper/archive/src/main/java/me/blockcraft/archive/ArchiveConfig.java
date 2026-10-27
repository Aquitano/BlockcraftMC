package me.blockcraft.archive;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;

public record ArchiveConfig(int maxPlayers, Duration unloadAfter, int noteMaxLength) {

    public static ArchiveConfig from(ConfigurationSection config) {
        return new ArchiveConfig(
                config.getInt("max-players"),
                Duration.ofSeconds(config.getLong("exhibits.unload-after-seconds")),
                config.getInt("notes.max-length"));
    }
}
