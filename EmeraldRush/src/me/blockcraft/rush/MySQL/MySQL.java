package me.blockcraft.rush.MySQL;

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

    public MySQL(final String user, final String pass, final String host2, final String dB) {
    }

    public static void connect() {
        if (!isConnected()) {
            try {
                MySQL.con = DriverManager.getConnection("jdbc:mysql://" + MySQL.host + ":" + MySQL.port + "/" + MySQL.database, MySQL.username, MySQL.password);
                Bukkit.getConsoleSender().sendMessage("\u00a74MySQL MySQL connected");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close() {
        if (isConnected()) {
            try {
                MySQL.con.close();
                Bukkit.getConsoleSender().sendMessage("\u00a74MySQL MySQL disconnected");
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
                MySQL.con.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS em_stats (UUID VARCHAR(100) , NAME VARCHAR(16), KILLS int, DEATHS int , WIN int , LOSE int , COINS int)");
                MySQL.con.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS em_kits (UUID VARCHAR(100), NAME VARCHAR(16), KIT1 int, KIT2 int, KIT3 int, KIT4 int, KIT5 int, KIT6 int, KIT7 int, KIT8 int, KIT9 int, KIT10 int, KIT11 int, KIT12 int, KIT13 int, KIT14 int, KIT15 int, KIT16 int)");
                Bukkit.getConsoleSender().sendMessage("\u00a74MySQL MySQL Table created");
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
            System.err.println(e);
        }
        return rs;
    }

    public static File getMySQLFile() {
        return new File("plugins/EmeraldRush", "MySQL.yml");
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
