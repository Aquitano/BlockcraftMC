package me.blockcraft.archive;

import me.blockcraft.api.config.BundledDefaults;
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

    public static ArchiveConfig from(ConfigurationSection yaml) {
        var config = BundledDefaults.strict(yaml);
        return new ArchiveConfig(
                config.integer("max-players"),
                Path.of(config.text("exhibits.directory")),
                Duration.ofSeconds(config.integer("exhibits.unload-after-seconds")),
                config.integer("notes.max-length"),
                config.texts("guide.collection-order"),
                config.texts("guide.suggested-visits"));
    }
}
