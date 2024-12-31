package me.blockcraft.coins;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CoinsAPI extends JavaPlugin {
    public static int i;
    public static String PluginPrefix;

    static {
        CoinsAPI.i = 5000;
        CoinsAPI.PluginPrefix = "\u00a78[\u00a79BlockcraftAPI\u00a78] \u00a77 ";
    }

    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage(CoinsAPI.PluginPrefix + "Plugin gestartet!");
        this.getCommand("coins").setExecutor(new CoinsCMD());

        MySQL.setStandardMySQL();
        MySQL.readMySQL();
        MySQL.connect();
        MySQL.createTable();
        if (!MySQL.isConnected()) {
            return;
        }
        final String Servername = MySQL.getMySQLFileConfiguration().getString("Server.Name");
        if (Servername.equals("null")) {
            Bukkit.getConsoleSender().sendMessage(CoinsAPI.PluginPrefix + "Servername nicht angegeben!");
            return;
        }
        CAPI.setMotd(Servername, 1);
    }

    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(CoinsAPI.PluginPrefix + "Plugin gestoppt!");
        final String Servername = MySQL.getMySQLFileConfiguration().getString("Server.Name");
        CAPI.setMotd(Servername, 0);
        MySQL.close();
    }
}
