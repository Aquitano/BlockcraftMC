package de.Blockcraft.BungeeSystem.MOTD;

import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public class Config {
    public static Settings Config;

    public static void loadConfig(Plugin p) {
        Config = null;
        Config = new Settings(p);
        if (Config.MaxPlayers == -1) {
            Config.MaxPlayers = ProxyServer.getInstance().getConfig().getPlayerLimit();
            try {
                Config.save();
            } catch (InvalidConfigurationException e) {

                e.printStackTrace();
            }
        }
    }

    public static boolean saveConfig() {
        try {
            Config.save();
            return true;
        } catch (InvalidConfigurationException var0) {
            return false;
        }
    }
}

