package me.blockcraft.system.report;

import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Reports live in {@code plugins/blockcraft-system/reports} and use the system's pool and identity rules.
 */
public record Reports(ReportCommand command, ReportService service) {

    public static Reports load(Object plugin, ProxyServer proxy, Logger logger, Path systemDirectory,
                               Database database, PlayerLookup lookup, PlayerVisibility visibility, PlayerChoices choices,
                               PlayerSettingRepository settings) {
        Path directory = systemDirectory.resolve("reports");
        var config = YamlFile.loadWithDefaults(directory.resolve("config.yml"), () -> Reports.class.getResourceAsStream("/reports/config.yml"));
        var loaded = Messages.load(directory, () -> Reports.class.getResourceAsStream("/reports/messages.yml"));
        var messages = loaded.with(loaded.tagsOf("palette"));
        var preferences = new NotifyPreferences(choices, settings);
        var repository = new ReportRepository(database);
        String permission = String.valueOf(config.getOrDefault("staff-permission", "blockcraft.report.notify"));
        var cooldown = Duration.ofSeconds(Math.max(0, ((Number) config.getOrDefault("report-cooldown-seconds", 10)).longValue()));
        var service = new ReportService(proxy, logger, repository, preferences, messages, permission, cooldown, lookup, visibility);
        return new Reports(new ReportCommand(plugin, proxy, preferences, messages,
                permission, service, visibility), service);
    }

}
