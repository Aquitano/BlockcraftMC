package de.Blockcraft.BungeeRanks;

import net.md_5.bungee.api.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.Properties;

public class MySQL {
    public static String username;
    public static String password;
    public static String database;
    public static String host;
    public static String port;
    public static Connection con;

    private final Plugin plugin;

    public MySQL(Plugin plugin) {
        this.plugin = plugin;
        readMySQLConfig();
    }

    public void connect() {
        if (!isConnected()) {
            try {
                con = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false",
                        username,
                        password
                );
                System.out.println("[BungeeRanks] MySQL connected.");
            } catch (SQLException e) {
                System.out.println("[BungeeRanks] Failed to connect to MySQL." + e);
            }
        }
    }

    public void close() {
        if (isConnected()) {
            try {
                con.close();
                System.out.println("[BungeeRanks] MySQL connection closed.");
            } catch (SQLException e) {
                System.out.println("[BungeeRanks] Failed to close MySQL connection." + e);
            }
        }
    }

    public boolean isConnected() {
        try {
            return con != null && !con.isClosed();
        } catch (SQLException e) {
            System.out.println("[BungeeRanks] MySQL connection check failed." + e);
            return false;
        }
    }

    public void createTable() {
        if (isConnected()) {
            String query = "CREATE TABLE IF NOT EXISTS Permission (" +
                    "UUID VARCHAR(100) PRIMARY KEY," +
                    "NAME VARCHAR(16)," +
                    "PREMIUM INT DEFAULT 0," +
                    "YOUTUBE INT DEFAULT 0," +
                    "DEV INT DEFAULT 0," +
                    "ADMIN INT DEFAULT 0," +
                    "OWNER INT DEFAULT 0" +
                    ")";
            try (Statement stmt = con.createStatement()) {
                stmt.executeUpdate(query);
                System.out.println("[BungeeRanks] Table 'Permission' created or already exists.");
            } catch (SQLException e) {
                System.out.println("[BungeeRanks] Failed to create table 'Permission'." + e);
            }
        }
    }

    public void update(String qry) {
        if (isConnected()) {
            try (Statement stmt = con.createStatement()) {
                stmt.executeUpdate(qry);
            } catch (SQLException e) {
                System.out.println("[BungeeRanks] Failed to execute update." + e);
            }
        }
    }

    public void update(String qry, String uuid) {
        if (isConnected()) {
            try (PreparedStatement stmt = con.prepareStatement(qry)) {
                stmt.setString(1, uuid);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("[BungeeRanks] Failed to execute update." + e);
            }
        }
    }

    private void readMySQLConfig() {
        File configFile = new File(plugin.getDataFolder(), "MySQL.yml");
        if (!configFile.exists()) {
            try {
                if (configFile.getParentFile() != null) {
                    configFile.getParentFile().mkdirs();
                }
                configFile.createNewFile();
                // Write default config
                String defaultConfig = """
                        username: root
                        password: password
                        database: your_database
                        host: localhost
                        port: 3306
                        """;
                Files.write(configFile.toPath(), defaultConfig.getBytes());
                System.out.println("[BungeeRanks] MySQL.yml created. Please configure it.");
            } catch (IOException e) {
                System.out.println("[BungeeRanks] Failed to create MySQL.yml." + e);
            }
        }

        // Read the configuration
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            Properties props = new Properties();
            props.load(reader);
            username = props.getProperty("username", "root");
            password = props.getProperty("password", "password");
            database = props.getProperty("database", "your_database");
            host = props.getProperty("host", "localhost");
            port = props.getProperty("port", "3306");
        } catch (IOException e) {
            System.out.println("[BungeeRanks] Failed to read MySQL.yml." + e);
        }
    }
}
