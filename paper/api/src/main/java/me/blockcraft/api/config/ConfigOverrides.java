package me.blockcraft.api.config;

import me.blockcraft.common.config.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * Once every plugin is up, names the keys a Blockcraft plugin's {@code config.yml} overrides that its bundled default
 * no longer has, so a renamed setting does not go quietly unused. Files read through {@link YamlFile} warn as they load.
 */
public final class ConfigOverrides implements Listener {

    @EventHandler
    public void onLoaded(ServerLoadEvent event) {
        for (var loaded : Bukkit.getPluginManager().getPlugins()) {
            if (!(loaded instanceof JavaPlugin plugin) || !plugin.getClass().getPackageName().startsWith("me.blockcraft")
                    || !plugin.getConfig().options().copyDefaults()) {
                continue;
            }
            var file = plugin.getDataPath().resolve("config.yml");
            try (InputStream bundled = plugin.getResource("config.yml")) {
                if (bundled == null || Files.notExists(file)) {
                    continue;
                }
                var defaults = YamlFile.load(bundled);
                try (InputStream overrides = Files.newInputStream(file)) {
                    var unknown = YamlFile.unknownKeys(defaults, YamlFile.load(overrides));
                    if (!unknown.isEmpty()) {
                        plugin.getSLF4JLogger().warn("{} sets keys this version does not know, so they do nothing; renamed or removed? {}",
                                file, unknown);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
    }
}
