package me.blockcraft.system.report;

import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Reports live in {@code plugins/blockcraft-system/reports} and use the system's pool and identity rules. The
 * retired BlockcraftReport plugin's files are copied over once so messages, cooldown and muted staff survive.
 */
public record Reports(ReportCommand command, ReportService service) {

    private static final List<String> LEGACY_FILES = List.of("config.yml", "messages.yml", "ignored-staff.yml");

    public static Reports load(Object plugin, ProxyServer proxy, Logger logger, Path systemDirectory,
                               Database database, PlayerLookup lookup, PlayerVisibility visibility, PluginDescription description) {
        Path directory = systemDirectory.resolve("reports");
        importLegacy(systemDirectory.resolveSibling("blockcraft-report"), directory);
        var config = YamlFile.loadWithDefaults(directory.resolve("config.yml"), () -> Reports.class.getResourceAsStream("/reports/config.yml"));
        var loaded = Messages.load(directory, () -> Reports.class.getResourceAsStream("/reports/messages.yml"));
        var messages = loaded.with(loaded.tagsOf("palette"));
        var preferences = new NotifyPreferences(directory.resolve("ignored-staff.yml"));
        var repository = new ReportRepository(database);
        String permission = String.valueOf(config.getOrDefault("staff-permission", "blockcraft.report.notify"));
        var cooldown = Duration.ofSeconds(Math.max(0, ((Number) config.getOrDefault("report-cooldown-seconds", 10)).longValue()));
        var service = new ReportService(proxy, logger, repository, preferences, messages, permission, cooldown, lookup, visibility);
        return new Reports(new ReportCommand(plugin, proxy, description, preferences, messages,
                permission, service, visibility), service);
    }

    public static void importLegacy(Path source, Path destination) {
        try {
            Files.createDirectories(destination);
            for (String name : LEGACY_FILES) {
                if (Files.exists(source.resolve(name)) && Files.notExists(destination.resolve(name))) {
                    Files.copy(source.resolve(name), destination.resolve(name));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not import report configuration", e);
        }
    }
}
