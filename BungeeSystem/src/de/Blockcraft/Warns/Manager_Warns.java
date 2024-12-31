package de.Blockcraft.Warns;

import de.Blockcraft.BungeeSystem.MOTD.Config;

import java.util.ArrayList;

public class Manager_Warns {
    public static ArrayList<String> listWarns() {
        return Config.Config.PlayerWarns;
    }

    public static Integer getWarns(String p) {
        for (String warn : Config.Config.PlayerWarns) {
            if (!warn.split(" ")[0].equalsIgnoreCase(p)) continue;
            return Integer.valueOf(warn.split(" ")[1]);
        }
        return 0;
    }

    public static void addWarn(String p) {
        for (String warn : Config.Config.PlayerWarns) {
            if (!warn.split(" ")[0].equalsIgnoreCase(p)) continue;
            Config.Config.PlayerWarns.remove(warn);
            Config.Config.PlayerWarns.add(p + " " + (Integer.parseInt(warn.split(" ")[1]) + 1));
            Config.saveConfig();
            return;
        }
        Config.Config.PlayerWarns.add(p + " 1");
        Config.saveConfig();
    }

    public static void setWarns(String p, Integer Warns) {
        for (String warn : Config.Config.PlayerWarns) {
            if (!warn.split(" ")[0].equalsIgnoreCase(p)) continue;
            Config.Config.PlayerWarns.remove(warn);
            if (Warns != 0) {
                Config.Config.PlayerWarns.add(p + " " + Warns);
            }
            Config.saveConfig();
            return;
        }
        if (Warns != 0) {
            Config.Config.PlayerWarns.add(p + " " + Warns);
            Config.saveConfig();
        }
    }
}

