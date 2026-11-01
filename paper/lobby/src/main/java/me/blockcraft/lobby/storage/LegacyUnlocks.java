package me.blockcraft.lobby.storage;

import me.blockcraft.api.shop.Unlocks;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Moves the per-server YAML stores of the old lobby ({@code <uuid>.<key>: true}) into {@code bc_unlocks}.
 * The file is renamed afterwards, so this runs once per lobby data folder.
 */
public final class LegacyUnlocks {

    private final Unlocks unlocks;
    private final Logger logger;

    public LegacyUnlocks(Unlocks unlocks, Logger logger) {
        this.unlocks = unlocks;
        this.logger = logger;
    }

    public void importYaml(Path file, Function<String, Optional<String>> itemForKey) {
        if (!Files.exists(file)) {
            return;
        }
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        int imported = 0;
        for (String uuid : yaml.getKeys(false)) {
            var section = yaml.getConfigurationSection(uuid);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(false)) {
                var item = itemForKey.apply(key);
                if (item.isPresent() && unlocks.unlock(UUID.fromString(uuid), item.get())) {
                    imported++;
                }
            }
        }
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".imported"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rename " + file + " after importing it", e);
        }
        logger.info("Imported {} unlocks from {} into bc_unlocks", imported, file.getFileName());
    }
}
