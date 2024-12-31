package me.blockcraft.reports;

import me.blockcraft.reports.commands.ReportCommand;
import me.blockcraft.reports.util.MySQL;
import me.blockcraft.reports.util.ReportsManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main extends Plugin {
    public final String prefix;

    public Main() {
        this.prefix = ChatColor.RED + "Reports " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
    }

    public void onEnable() {
        this.createConfig();
        this.fetchConfig();
        MySQL.Connect();
        MySQL.createTable();
        this.registerCommands();
        System.out.println("[Reports] Plugin erfolgreich aktiviert!");
        ProxyServer.getInstance().getScheduler().schedule(this, () -> {
            if (MySQL.isConnected()) {
                System.out.println("[Reports-SQL] Es gibt aktuell " + ReportsManager.getReportLists("Reportedname").size() + " Reports!");
                System.out.println("[Reports-SQL] MySQL Verbindung aktualisiert! ");
            }
        }, 10L, 1800L, TimeUnit.SECONDS);
    }

    public void onDisable() {
        MySQL.close();
        System.out.println("[Reports] Plugin erfolgreich deaktiviert!");
    }

    public void registerCommands() {
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new ReportCommand("report", this));
    }

    public void createConfig() {
        try {
            if (!this.getDataFolder().exists()) {
                this.getDataFolder().mkdir();
            }
            final File file = new File(this.getDataFolder().getPath(), "config.yml");
            if (!file.exists()) {
                file.createNewFile();
                final Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
                config.set("MySQL.hostname", "localhost");
                config.set("MySQL.port", "3306");
                config.set("MySQL.database", "database");
                config.set("MySQL.username", "user");
                config.set("MySQL.password", "password");
                ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, file);
            }
        } catch (final IOException ex) {
        }
    }

    public void fetchConfig() {
        final File file = new File(this.getDataFolder().getPath(), "config.yml");
        try {
            final Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
            MySQL.host = config.getString("MySQL.hostname");
            MySQL.port = config.getString("MySQL.port");
            MySQL.database = config.getString("MySQL.database");
            MySQL.user = config.getString("MySQL.username");
            MySQL.password = config.getString("MySQL.password");
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }
}
