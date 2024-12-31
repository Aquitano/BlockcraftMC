package me.blockcraft.coins;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CAPI {
    public static boolean playerExists(final String uuid) {
        try {
            final ResultSet rs = MySQL.getResult("SELECT * FROM VCOINS WHERE UUID='" + uuid + "'");
            return rs.next() && rs.getString("UUID") != null;
        } catch (SQLException e) {
            e.printStackTrace();
            MySQL.close();
            MySQL.setStandardMySQL();
            MySQL.readMySQL();
            MySQL.connect();
            MySQL.createTable();
            return false;
        }
    }

    public static boolean doesServerExist(final String Servername) {
        try {
            final ResultSet rs = MySQL.getResult("SELECT * FROM MOTDS WHERE SERVER='" + Servername + "'");
            return rs.next() && rs.getString("SERVER") != null;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void createPlayer(final String uuid) {
        if (!playerExists(uuid)) {
            MySQL.update("INSERT INTO VCOINS(UUID, NAME, COINS) VALUES ('" + uuid + "', '" + "Name" + "', '10');");
        }
    }

    public static void addCoins(final String uuid, final Integer coins) {
        if (playerExists(uuid)) {
            setCoins(uuid, getCoins(uuid) + coins);
        } else {
            createPlayer(uuid);
            setCoins(uuid, coins);
        }
    }

    public static Integer getCoins(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM VCOINS WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("COINS");
                }
                i = rs.getInt("COINS");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            getCoins(uuid);
        }
        return i;
    }

    public static void setCoins(final String uuid, final int i) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE VCOINS SET COINS='" + i + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setCoins(uuid, i);
        }
    }

    public static void setMotd(final String servername, final int motd) {
        MySQL.update("UPDATE MOTDS SET MOTD='" + motd + "' WHERE SERVER='" + servername + "'");
    }

}
