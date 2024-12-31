package me.blockcraft.MySQL;

import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;

public class KitAPI {
    public static boolean playerExists(String uuid) {
        try {
            ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
            if (rs.next()) {
                return rs.getString("UUID") != null;
            }
            return false;
        } catch (SQLException e) {
            MySQL.close();
            MySQL.setStandardMySQL();
            MySQL.readMySQL();
            MySQL.connect();
            MySQL.createTable();
            return false;
        }
    }

    public static void createPlayer(String uuid) {
        if (!KitAPI.playerExists(uuid)) {
            MySQL.update("INSERT INTO qsg_kits (UUID, NAME, KIT1, KIT2, KIT3, KIT4, KIT5, KIT6, KIT7, KIT8) VALUES ('" + uuid + "', '" + "Name" + "', '0', '0', '0', '0', '0', '0', '0', '0');");
        }
    }

    public static Integer hasrKit1(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT1"));
                }
                i = rs.getInt("KIT1");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit1(uuid);
        }
        return i;
    }

    public static Integer hasrKit2(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT2"));
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
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit2(uuid);
        }
        return i;
    }

    public static Integer hasrKit3(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT3"));
                }
                i = rs.getInt("KIT3");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit3(uuid);
        }
        return i;
    }

    public static Integer hasrKit4(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT4"));
                }
                i = rs.getInt("KIT4");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit4(uuid);
        }
        return i;
    }

    public static Integer hasrKit5(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT5"));
                }
                i = rs.getInt("KIT5");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit5(uuid);
        }
        return i;
    }

    public static Integer hasrKit6(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT6"));
                }
                i = rs.getInt("KIT6");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit6(uuid);
        }
        return i;
    }

    public static Integer hasrKit7(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT7"));
                }
                i = rs.getInt("KIT7");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit7(uuid);
        }
        return i;
    }

    public static Integer hasrKit8(String uuid) {
        int i = 0;
        if (KitAPI.playerExists(uuid)) {
            try {
                ResultSet rs = MySQL.getResult("SELECT * FROM qsg_kits WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    Integer.valueOf(rs.getInt("KIT8"));
                }
                i = rs.getInt("KIT8");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.hasrKit8(uuid);
        }
        return i;
    }

    public static void setKit1(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT1='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit1(uuid, name);
        }
    }

    public static void setKit2(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT2='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit2(uuid, name);
        }
    }

    public static void setKit3(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT3='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit3(uuid, name);
        }
    }

    public static void setKit4(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT4='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit4(uuid, name);
        }
    }

    public static void setKit5(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT5='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit5(uuid, name);
        }
    }

    public static void setKit6(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT6='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit6(uuid, name);
        }
    }

    public static void setKit7(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT7='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit7(uuid, name);
        }
    }

    public static void setKit8(String uuid, String name) {
        if (KitAPI.playerExists(uuid)) {
            MySQL.update("UPDATE qsg_kits SET KIT8='1' WHERE UUID='" + uuid + "'");
            MySQL.update("UPDATE qsg_kits SET NAME='" + name + "' WHERE UUID='" + uuid + "'");
        } else {
            KitAPI.createPlayer(uuid);
            KitAPI.setKit8(uuid, name);
        }
    }

    public static boolean hasbuyedKit1(Player p) {
        return KitAPI.hasrKit1(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit2(Player p) {
        return KitAPI.hasrKit2(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit3(Player p) {
        return KitAPI.hasrKit3(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit4(Player p) {
        return KitAPI.hasrKit4(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit5(Player p) {
        return KitAPI.hasrKit5(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit6(Player p) {
        return KitAPI.hasrKit6(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit7(Player p) {
        return KitAPI.hasrKit7(p.getUniqueId().toString()) == 1;
    }

    public static boolean hasbuyedKit8(Player p) {
        return KitAPI.hasrKit8(p.getUniqueId().toString()) == 1;
    }
}

