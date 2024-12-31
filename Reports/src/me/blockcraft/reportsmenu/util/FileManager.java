package me.blockcraft.reportsmenu.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class FileManager {
    public static File getConfigFile() {
        return new File("plugins/ReportsMenu", "config.yml");
    }

    public static FileConfiguration getConfigFileConfiguration() {
        return YamlConfiguration.loadConfiguration(getConfigFile());
    }

    public static void createStandardConfig() {
        final FileConfiguration cfg = getConfigFileConfiguration();
        cfg.options().copyDefaults(true);
        cfg.addDefault("MySQL.host", "localhost");
        cfg.addDefault("MySQL.port", "3306");
        cfg.addDefault("MySQL.database", "database");
        cfg.addDefault("MySQL.username", "username");
        cfg.addDefault("MySQL.password", "password");
        try {
            cfg.save(getConfigFile());
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public static void fetchData() {
        final FileConfiguration cfg = getConfigFileConfiguration();
        MySQL.user = cfg.getString("MySQL.username");
        MySQL.password = cfg.getString("MySQL.password");
        MySQL.port = cfg.getString("MySQL.port");
        MySQL.host = cfg.getString("MySQL.host");
        MySQL.database = cfg.getString("MySQL.database");
    }
}
