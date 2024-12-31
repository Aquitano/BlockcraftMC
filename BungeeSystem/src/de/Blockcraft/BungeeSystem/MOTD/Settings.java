package de.Blockcraft.BungeeSystem.MOTD;

import de.Blockcraft.BungeeSystem.Data;
import net.cubespace.Yamler.Config.Config;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;

@SuppressWarnings("deprecation")
public class Settings
        extends Config {
    public Integer MaxPlayers = 1;
    public Boolean isLocked = false;
    public Boolean noIPs = true;
    public Boolean AutoMessagesEnabled = false;
    public String AutoMessagesPrefix = "&3[&bAutoMessager&3]&7";
    public final String Prefix = Data.PREFIX;
    public ArrayList<String> AutoMessages = new ArrayList<>();
    public final Boolean useMotds = true;
    public String DefaultMotd = "&cBlockcraft.net";
    public final ArrayList<String> RandomMotds = new ArrayList<>();
    public ArrayList<String> MutedPlayers = new ArrayList<>();
    public final ArrayList<String> MutedWerbung = new ArrayList<>();
    public final ArrayList<String> Werbungsserver = new ArrayList<>();
    public final Integer MaxWarns = 8;
    public final ArrayList<String> PlayerWarns = new ArrayList<>();
    public final ArrayList<String> BannedPlayers = new ArrayList<>();
    public Integer TimeBanDays = 0;

    public Settings(Plugin plugin) {
        this.CONFIG_HEADER = new String[]{"Einstellungen f\ufffdr System"};
        this.CONFIG_FILE = new File(plugin.getDataFolder(), "einstellungen.yml");
        try {
            this.init();
        } catch (InvalidConfigurationException ex) {
            ex.printStackTrace();
        }
    }
}

