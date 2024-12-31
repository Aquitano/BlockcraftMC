package me.blockcraft.rush.MySQL;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StatsAPI {
    public static boolean playerExists(final String uuid) {
        try {
            final ResultSet rs = MySQL.getResult("SELECT * FROM em_stats WHERE UUID='" + uuid + "'");
            return rs.next() && rs.getString("UUID") != null;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void createPlayer(final String uuid) {
        if (!playerExists(uuid)) {
            MySQL.update("INSERT INTO em_stats (UUID ,NAME , KILLS, DEATHS, WIN, LOSE, COINS) VALUES ('" + uuid + "'," + "Name" + ", '0', '0', '0', '0', '0');");
        }
    }

    public static Integer getKills(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KILLS");
                }
                i = rs.getInt("KILLS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            getKills(uuid);
        }
        return i;
    }

    public static Integer getCoins(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("COINS");
                }
                i = rs.getInt("COINS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            getKills(uuid);
        }
        return i;
    }

    public static Integer getWins(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("WIN");
                }
                i = rs.getInt("WIN");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            getWins(uuid);
        }
        return i;
    }

    public static Integer getLoses(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("LOSE");
                }
                i = rs.getInt("LOSE");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            getLoses(uuid);
        }
        return i;
    }

    public static Integer getDeaths(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("DEATHS");
                }
                i = rs.getInt("DEATHS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            getDeaths(uuid);
        }
        return i;
    }

    public static void setKills(final String uuid, final Integer kills, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_stats SET KILLS='" + kills + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKills(uuid, kills, name);
        }
    }

    public static void setCoins(final String uuid, final Integer coins, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_stats SET COINS='" + coins + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setCoins(uuid, coins, name);
        }
    }

    public static void setDeaths(final String uuid, final Integer deaths, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_stats SET DEATHS='" + deaths + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setDeaths(uuid, deaths, name);
        }
    }

    public static void setWins(final String uuid, final Integer wins, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_stats SET WIN='" + wins + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setWins(uuid, wins, name);
        }
    }

    public static void setLose(final String uuid, final Integer lose, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_stats SET LOSE='" + lose + "' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_stats SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setLose(uuid, lose, name);
        }
    }

    public static void removeKills(final String uuid, final Integer kills, final String name) {
        if (playerExists(uuid)) {
            setKills(uuid, getKills(uuid) - kills, name);
        } else {
            createPlayer(uuid);
            removeKills(uuid, kills, name);
        }
    }

    public static void removeCoins(final String uuid, final Integer coins, final String name) {
        if (playerExists(uuid)) {
            setCoins(uuid, getCoins(uuid) - coins, name);
        } else {
            createPlayer(uuid);
            removeKills(uuid, coins, name);
        }
    }

    public static void removeDeaths(final String uuid, final Integer deaths, final String name) {
        if (playerExists(uuid)) {
            setKills(uuid, getDeaths(uuid) - deaths, name);
        } else {
            createPlayer(uuid);
            removeDeaths(uuid, deaths, name);
        }
    }
}
