package me.blockcraft.reportsmenu.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MySQL {
    public static String host;
    public static String port;
    public static String database;
    public static String user;
    public static String password;
    public static Connection con;

    public MySQL() {

    }

    public static void Connect() {
        if (!isConnected()) {
            try {
                MySQL.con = DriverManager.getConnection("jdbc:mysql://" + MySQL.host + ":" + MySQL.port + "/" + MySQL.database, MySQL.user, MySQL.password);
                System.out.println("[Reports] MySQL-Verbindung hergestellt!");
            } catch (final SQLException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public static void close() {
        if (isConnected()) {
            try {
                MySQL.con.close();
                System.out.println("[Reports] MySQL-Verbindung getrennt!");
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isConnected() {
        return MySQL.con != null;
    }

    public static void createTable() {
        try {
            MySQL.con.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS Reports (ID INT(100) NOT NULL AUTO_INCREMENT PRIMARY KEY, Reportername VARCHAR(100), Reportedname VARCHAR(100), Grund VARCHAR(1000), Server VARCHAR(100), Status VARCHAR(100))");
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public static void update(final String qry) {
        if (isConnected()) {
            try {
                MySQL.con.createStatement().executeUpdate(qry);
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static ResultSet getResult(final String qry) {
        if (isConnected()) {
            try {
                return MySQL.con.createStatement().executeQuery(qry);
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
