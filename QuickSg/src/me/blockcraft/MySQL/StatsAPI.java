package me.blockcraft.MySQL;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StatsAPI {
    public static boolean playerExists(String uuid) {
        try {
            ResultSet rs = MySQL.getResult("SELECT * FROM qsg_stats WHERE UUID='" + uuid + "'");
            if (rs.next()) {
                return rs.getString("UUID") != null;
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void createPlayer(String uuid) {
        if (!StatsAPI.playerExists(uuid)) {
            MySQL.update("INSERT INTO qsg_stats (UUID ,NAME , KILLS, DEATHS, WIN, LOSE, COINS) VALUES ('" + uuid + "'," + "Name" + ", '0', '0', '0', '0', '0');");
        }
    }

    public static Integer getKills(String uuid) {
        int i = 0;
        if (StatsAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KILLS"));
                }
                i = rs.getInt("KILLS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.getKills(uuid);
        }
        return i;
    }

    public static Integer getCoins(String uuid) {
        int i = 0;
        if (StatsAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("COINS"));
                }
                i = rs.getInt("COINS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.getKills(uuid);
        }
        return i;
    }

    public static Integer getWins(String uuid) {
        int i = 0;
        if (StatsAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("WIN"));
                }
                i = rs.getInt("WIN");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.getWins(uuid);
        }
        return i;
    }

    public static Integer getLoses(String uuid) {
        int i = 0;
        if (StatsAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("LOSE"));
                }
                i = rs.getInt("LOSE");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.getLoses(uuid);
        }
        return i;
    }

    public static Integer getDeaths(String uuid) {
        int i = 0;
        if (StatsAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("DEATHS"));
                }
                i = rs.getInt("DEATHS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.getDeaths(uuid);
        }
        return i;
    }

    public static void setKills(String uuid, Integer kills, String name) {
        if (StatsAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_stats SET KILLS='" + kills + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.setKills(uuid, kills, name);
        }
    }

    public static void setCoins(String uuid, Integer coins, String name) {
        if (StatsAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_stats SET COINS='" + coins + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.setCoins(uuid, coins, name);
        }
    }

    public static void setDeaths(String uuid, Integer deaths, String name) {
        if (StatsAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_stats SET DEATHS='" + deaths + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.setDeaths(uuid, deaths, name);
        }
    }

    public static void setWins(String uuid, Integer wins, String name) {
        if (StatsAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_stats SET WIN='" + wins + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.setWins(uuid, wins, name);
        }
    }

    public static void setLose(String uuid, Integer lose, String name) {
        if (StatsAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_stats SET LOSE='" + lose + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.setLose(uuid, lose, name);
        }
    }

    public static void removeKills(String uuid, Integer kills, String name) {
        if (StatsAPI.playerExists(uuid)) {
            StatsAPI.setKills(uuid, StatsAPI.getKills(uuid) - kills, name);
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.removeKills(uuid, kills, name);
        }
    }

    public static void removeCoins(String uuid, Integer coins, String name) {
        if (StatsAPI.playerExists(uuid)) {
            StatsAPI.setCoins(uuid, StatsAPI.getCoins(uuid) - coins, name);
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.removeKills(uuid, coins, name);
        }
    }

    public static void removeDeaths(String uuid, Integer deaths, String name) {
        if (StatsAPI.playerExists(uuid)) {
            StatsAPI.setKills(uuid, StatsAPI.getDeaths(uuid) - deaths, name);
        } else {
            StatsAPI.createPlayer(uuid);
            StatsAPI.removeDeaths(uuid, deaths, name);
        }
    }
}

