package me.blockcraft.perms.MySQL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class MySQL {
    private static final Logger log = LogManager.getLogger(MySQL.class);
    public static String username;
    public static String password;
    public static String database;
    public static String host;
    public static String port;
    public static Connection con;

    public MySQL(final String user, final String pass, final String host2, final String dB) {
    }

    public static void connect() {
        if (!isConnected()) {
            try {
                MySQL.con = DriverManager.getConnection("jdbc:mysql://" + MySQL.host + ":" + MySQL.port + "/" + MySQL.database, MySQL.username, MySQL.password);
                Bukkit.getConsoleSender().sendMessage("\u00a78[\u00a79BlockcraftPerms\u00a78] \u00a77MySQL MySQL connected");
            } catch (SQLException e) {
                e.printStackTrace();
                connect();
            }
        }
    }

    public static void close() {
        if (isConnected()) {
            try {
                MySQL.con.close();
                Bukkit.getConsoleSender().sendMessage("\u00a78[\u00a79BlockcraftPerms\u00a78] \u00a77MySQL MySQL disconnected");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isConnected() {
        return MySQL.con != null;
    }

    public static void createTable() {
        if (isConnected()) {
            try {
                String query = "CREATE TABLE IF NOT EXISTS Permission (" +
                        "UUID VARCHAR(100) PRIMARY KEY," +
                        "NAME VARCHAR(16)," +
                        "PREMIUM INT DEFAULT 0," +
                        "YOUTUBE INT DEFAULT 0," +
                        "DEV INT DEFAULT 0," +
                        "ADMIN INT DEFAULT 0," +
                        "OWNER INT DEFAULT 0" +
                        ")";
                MySQL.con.createStatement().executeUpdate(query);
                Bukkit.getConsoleSender().sendMessage("\u00a78[\u00a79BlockcraftPerms\u00a78] \u00a77MySQL MySQL Table created");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void update(final String qry) {
        if (isConnected()) {
            try {
                MySQL.con.createStatement().executeUpdate(qry);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static ResultSet getResult(final String qry) {
        ResultSet rs = null;
        try {
            final Statement st = MySQL.con.createStatement();
            rs = st.executeQuery(qry);
        } catch (SQLException e) {
            connect();
            log.error("e: ", e);
        }
        return rs;
    }

    public static File getMySQLFile() {
        return new File("plugins/Perms", "MySQL.yml");
    }

    public static FileConfiguration getMySQLFileConfiguration() {
        return YamlConfiguration.loadConfiguration(getMySQLFile());
    }

    public static void setStandardMySQL() {
        final FileConfiguration cfg = getMySQLFileConfiguration();
        cfg.options().copyDefaults(true);
        cfg.addDefault("username", "root");
        cfg.addDefault("password", "password");
        cfg.addDefault("database", "localhost");
        cfg.addDefault("host", "localhost");
        cfg.addDefault("port", "3306");
        try {
            cfg.save(getMySQLFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readMySQL() {
        final FileConfiguration cfg = getMySQLFileConfiguration();
        MySQL.username = cfg.getString("username");
        MySQL.password = cfg.getString("password");
        MySQL.database = cfg.getString("database");
        MySQL.host = cfg.getString("host");
        MySQL.port = cfg.getString("port");
    }
}
