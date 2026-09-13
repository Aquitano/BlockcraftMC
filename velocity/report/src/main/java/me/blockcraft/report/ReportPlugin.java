package me.blockcraft.report;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import me.blockcraft.build.BuildConstants;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.message.Messages;
import me.blockcraft.report.command.ReportCommand;
import me.blockcraft.report.storage.NotifyPreferences;
import me.blockcraft.common.report.ReportRepository;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;

@Plugin(id = "blockcraft-report", name = "BlockcraftReport", version = BuildConstants.VERSION, authors = {"Aquitano"})
public final class ReportPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private Database database;

    @Inject
    public ReportPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        var config = YamlFile.loadWithDefaults(dataDirectory.resolve("config.yml"), () -> getClass().getResourceAsStream("/config.yml"));
        database = Database.connect(DatabaseConfig.fromYaml(YamlFile.section(config, "database")), "BlockcraftReport");
        var reports = new ReportRepository(database);
        reports.createTable();

        var loaded = Messages.load(dataDirectory, () -> getClass().getResourceAsStream("/messages.yml"));
        var messages = loaded.with(loaded.tagsOf("palette"));
        var preferences = new NotifyPreferences(dataDirectory.resolve("ignored-staff.yml"));
        var description = proxy.getPluginManager().fromInstance(this).map(PluginContainer::getDescription).orElseThrow();
        var command = new ReportCommand(this, proxy, logger, description, reports, preferences, messages,
                String.valueOf(config.getOrDefault("staff-permission", "blockcraft.report.notify")),
                Duration.ofSeconds(((Number) config.getOrDefault("report-cooldown-seconds", 10)).longValue()));

        var commandManager = proxy.getCommandManager();
        commandManager.register(commandManager.metaBuilder("report").plugin(this).build(), command.create());
        logger.info("BlockcraftReport enabled");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (database != null) {
            database.close();
        }
    }
}
