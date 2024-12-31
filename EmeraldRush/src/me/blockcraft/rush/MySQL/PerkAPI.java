package me.blockcraft.rush.MySQL;

import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PerkAPI {
    public static boolean playerExists(final String uuid) {
        try {
            final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
            return rs.next() && rs.getString("UUID") != null;
        } catch (SQLException e) {
            MySQL.close();
            MySQL.setStandardMySQL();
            MySQL.readMySQL();
            MySQL.connect();
            MySQL.createTable();
            return false;
        }
    }

    public static void createPlayer(final String uuid) {
        if (!playerExists(uuid)) {
            MySQL.update("INSERT INTO em_kits (UUID, NAME, KIT1, KIT2, KIT3, KIT4, KIT5, KIT6, KIT7, KIT8) VALUES ('" + uuid + "', '" + "Name" + "', '0', '0', '0', '0', '0', '0', '0', '0');");
        }
    }

    public static Integer hasrKit1(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT1");
                }
                i = rs.getInt("KIT1");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit1(uuid);
        }
        return i;
    }

    public static Integer hasrKit2(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT2");
                }
                i = rs.getInt("KIT2");
            } catch (SQLException e) {
                e.printStackTrace();
                MySQL.close();
                MySQL.setStandardMySQL();
                MySQL.readMySQL();
                MySQL.connect();
                MySQL.createTable();
            }
        } else {
            createPlayer(uuid);
            hasrKit2(uuid);
        }
        return i;
    }

    public static Integer hasrKit3(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT3");
                }
                i = rs.getInt("KIT3");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit3(uuid);
        }
        return i;
    }

    public static Integer hasrKit4(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT4");
                }
                i = rs.getInt("KIT4");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit4(uuid);
        }
        return i;
    }

    public static Integer hasrKit5(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT5");
                }
                i = rs.getInt("KIT5");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit5(uuid);
        }
        return i;
    }

    public static Integer hasrKit6(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT6");
                }
                i = rs.getInt("KIT6");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit6(uuid);
        }
        return i;
    }

    public static Integer hasrKit7(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT7");
                }
                i = rs.getInt("KIT7");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit7(uuid);
        }
        return i;
    }

    public static Integer hasrKit8(final String uuid) {
        int i = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT * FROM em_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    rs.getInt("KIT8");
                }
                i = rs.getInt("KIT8");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            createPlayer(uuid);
            hasrKit8(uuid);
        }
        return i;
    }

    public static void setKit1(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT1='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit1(uuid, name);
        }
    }

    public static void setKit2(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT2='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit2(uuid, name);
        }
    }

    public static void setKit3(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT3='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit3(uuid, name);
        }
    }

    public static void setKit4(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT4='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit4(uuid, name);
        }
    }

    public static void setKit5(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT5='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit5(uuid, name);
        }
    }

    public static void setKit6(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT6='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit6(uuid, name);
        }
    }

    public static void setKit7(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT7='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit7(uuid, name);
        }
    }

    public static void setKit8(final String uuid, final String name) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE em_kits SET KIT8='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE em_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            setKit8(uuid, name);
        }
    }

    public static boolean hasbuyedKit1(final Player p) {
        return hasrKit1(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit2(final Player p) {
        return hasrKit2(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit3(final Player p) {
        return hasrKit3(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit4(final Player p) {
        return hasrKit4(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit5(final Player p) {
        return hasrKit5(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit6(final Player p) {
        return hasrKit6(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit7(final Player p) {
        return hasrKit7(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit8(final Player p) {
        return hasrKit8(p.getUniqueId().toString()) == 1;
    }
}
