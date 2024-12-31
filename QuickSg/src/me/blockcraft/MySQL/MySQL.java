/*
 * Decompiled with CFR 0_118.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.FileConfigurationOptions
 *  org.bukkit.configuration.file.YamlConfiguration
 */
package me.blockcraft.MySQL;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class MySQL {
    public static String username;
    public static String password;
    public static String database;
    public static String host;
    public static String port;
    public static Connection con;

    public MySQL(String user, String pass, String host2, String dB) {
    }

    public static void connect() {
        if (!MySQL.isConnected()) {
            try {
                con = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
                Bukkit.getConsoleSender().sendMessage("\u00a74MySQL MySQL connected");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close() {
        if (MySQL.isConnected()) {
            try {
                con.close();
                Bukkit.getConsoleSender().sendMessage("\u00a74MySQL MySQL disconnected");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isConnected() {
        return con != null;
    }

    public static void createTable() {
        if (MySQL.isConnected()) {
            try {
                con.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS qsg_stats (UUID VARCHAR(100) , NAME VARCHAR(16), KILLS int, DEATHS int , WIN int , LOSE int , COINS int)");
                con.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS qsg_kits (UUID VARCHAR(100), NAME VARCHAR(16), KIT1 int, KIT2 int, KIT3 int, KIT4 int, KIT5 int, KIT6 int, KIT7 int, KIT8 int)");
                Bukkit.getConsoleSender().sendMessage("\u00a74MySQL MySQL Table created");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void update(String qry) {
        if (MySQL.isConnected()) {
            try {
                con.createStatement().executeUpdate(qry);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static ResultSet getResult(String qry) {
        ResultSet rs = null;
        try {
            Statement st = con.createStatement();
            rs = st.executeQuery(qry);
        } catch (SQLException e) {
            MySQL.connect();
            System.err.println(e);
        }
        return rs;
    }

    public static File getMySQLFile() {
        return new File("plugins/QuickSG", "MySQL.yml");
    }

    public static FileConfiguration getMySQLFileConfiguration() {
        return YamlConfiguration.loadConfiguration(MySQL.getMySQLFile());
    }

    public static void setStandardMySQL() {
        FileConfiguration cfg = MySQL.getMySQLFileConfiguration();
        cfg.options().copyDefaults(true);
        cfg.addDefault("username", "root");
        cfg.addDefault("password", "password");
        cfg.addDefault("database", "localhost");
        cfg.addDefault("host", "localhost");
        cfg.addDefault("port", "3306");
        try {
            cfg.save(MySQL.getMySQLFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readMySQL() {
        FileConfiguration cfg = MySQL.getMySQLFileConfiguration();
        username = cfg.getString("username");
        password = cfg.getString("password");
        database = cfg.getString("database");
        host = cfg.getString("host");
        port = cfg.getString("port");
    }
}

