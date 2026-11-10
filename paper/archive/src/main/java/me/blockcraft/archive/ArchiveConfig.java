package me.blockcraft.archive;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record ArchiveConfig(int maxPlayers, Path exhibits, Duration unloadAfter, int noteMaxLength,
                            List<String> collectionOrder, List<String> suggestedVisits) {

    public ArchiveConfig {
        collectionOrder = List.copyOf(collectionOrder);
        suggestedVisits = List.copyOf(suggestedVisits);
    }

    public static ArchiveConfig from(ConfigurationSection config) {
        return new ArchiveConfig(
                config.getInt("max-players"),
                Path.of(config.getString("exhibits.directory", "/exhibits")),
                Duration.ofSeconds(config.getLong("exhibits.unload-after-seconds")),
                config.getInt("notes.max-length"),
                config.getStringList("guide.collection-order"),
                config.getStringList("guide.suggested-visits"));
    }
}
