package me.blockcraft.duell.methods;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StatsAPI {
    public static boolean playerExists(final String uuid) {
        try {
            final ResultSet rs = MySQL.getResult("SELECT * FROM duell_stats WHERE UUID='" + uuid + "'");
            return rs.next() && rs.getString("UUID") != null;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void createPlayer(final String uuid, final String name) {
        if (!playerExists(uuid)) {
            MySQL.update("INSERT INTO duell_stats (UUID, NAME, KILLS, DEATHS, WIN, LOSE, COINS) VALUES ('" + uuid + "', '" + name + "', '0', '0', '0', '0', '100');");
        }
    }

    public static Integer getKills(final String uuid, final String name) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM duell_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KILLS");
                }
                i = rs.getInt("KILLS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid, name);
            getKills(uuid, name);
        }
        return i;
    }

    public static Integer getCoins(final String uuid, final String name) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM duell_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("COINS");
                }
                i = rs.getInt("COINS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid, name);
            getKills(uuid, name);
        }
        return i;
    }

    public static Integer getWins(final String uuid, final String name) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM duell_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("WIN");
                }
                i = rs.getInt("WIN");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid, name);
            getWins(uuid, name);
        }
        return i;
    }

    public static Integer getLoses(final String uuid, final String name) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM duell_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("LOSE");
                }
                i = rs.getInt("LOSE");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid, name);
            getLoses(uuid, name);
        }
        return i;
    }

    public static Integer getDeaths(final String uuid, final String name) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM duell_stats WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("DEATHS");
                }
                i = rs.getInt("DEATHS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid, name);
            getDeaths(uuid, name);
        }
        return i;
    }

    public static void setKills(final String uuid, final String name, final Integer kills) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE duell_stats SET KILLS='" + kills + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid, name);
            setKills(uuid, name, kills);
        }
    }

    public static void setCoins(final String uuid, final String name, final Integer coins) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE duell_stats SET COINS='" + coins + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid, name);
            setCoins(uuid, name, coins);
        }
    }

    public static void setDeaths(final String uuid, final String name, final Integer deaths) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE duell_stats SET DEATHS='" + deaths + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid, name);
            setDeaths(uuid, name, deaths);
        }
    }

    public static void setWins(final String uuid, final String name, final Integer wins) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE duell_stats SET WIN='" + wins + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid, name);
            setWins(uuid, name, wins);
        }
    }

    public static void setLose(final String uuid, final String name, final Integer lose) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE duell_stats SET LOSE='" + lose + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid, name);
            setLose(uuid, name, lose);
        }
    }

    public static void addKills(final String uuid, final String name, final Integer kills) {
        if (playerExists(uuid)) {
            setKills(uuid, name, getKills(uuid, name) + kills);
        } else {
            createPlayer(uuid, name);
            addKills(uuid, name, kills);
        }
    }

    public static void addCoins(final String uuid, final String name, final Integer coins) {
        if (playerExists(uuid)) {
            setCoins(uuid, name, getCoins(uuid, name) + coins);
        } else {
            createPlayer(uuid, name);
            addCoins(uuid, name, coins);
        }
    }

    public static void addDeaths(final String uuid, final String name, final Integer deaths) {
        if (playerExists(uuid)) {
            setDeaths(uuid, name, getDeaths(uuid, name) + deaths);
        } else {
            createPlayer(uuid, name);
            addDeaths(uuid, name, deaths);
        }
    }

    public static void addWin(final String uuid, final String name, final Integer wins) {
        if (playerExists(uuid)) {
            setWins(uuid, name, getWins(uuid, name) + wins);
        } else {
            createPlayer(uuid, name);
            addWin(uuid, name, wins);
        }
    }

    public static void addLose(final String uuid, final String name, final Integer lose) {
        if (playerExists(uuid)) {
            setLose(uuid, name, getLoses(uuid, name) + lose);
        } else {
            createPlayer(uuid, name);
            addLose(uuid, name, lose);
        }
    }

    public static void removeKills(final String uuid, final String name, final Integer kills) {
        if (playerExists(uuid)) {
            setKills(uuid, name, getKills(uuid, name) - kills);
        } else {
            createPlayer(uuid, name);
            removeKills(uuid, name, kills);
        }
    }

    public static void removeCoins(final String uuid, final String name, final Integer coins) {
        if (playerExists(uuid)) {
            setCoins(uuid, name, getCoins(uuid, name) - coins);
        } else {
            createPlayer(uuid, name);
            removeKills(uuid, name, coins);
        }
    }

    public static void removeDeaths(final String uuid, final String name, final Integer deaths) {
        if (playerExists(uuid)) {
            setKills(uuid, name, getDeaths(uuid, name) - deaths);
        } else {
            createPlayer(uuid, name);
            removeDeaths(uuid, name, deaths);
        }
    }
}
