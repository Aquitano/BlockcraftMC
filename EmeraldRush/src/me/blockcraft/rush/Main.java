package me.blockcraft.rush;

import me.blockcraft.rush.Commands.*;
import me.blockcraft.rush.Listener.*;
import me.blockcraft.rush.MySQL.MySQL;
import me.blockcraft.rush.Perks.*;
import me.blockcraft.rush.Scoreboards.*;
import me.blockcraft.rush.Shop.*;
import me.blockcraft.rush.tools.*;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class Main extends JavaPlugin {
    public static boolean State_Lobby;
    public static boolean State_InGame;
    public static boolean State_DM;
    public static boolean State_Ending;
    public static boolean State_Shopping;
    public static boolean State_SuchZeit;
    public static String Prefix;
    public static Main instance;
    public static ArrayList<Player> online;
    public static ArrayList<Player> spectator;
    public static ArrayList<Player> builder;

    static {
        Main.State_Lobby = true;
        Main.State_InGame = false;
        Main.State_DM = false;
        Main.State_Ending = false;
        Main.State_Shopping = false;
        Main.State_SuchZeit = false;
        Main.Prefix = Data.TW_PREFIX;
        Main.online = new ArrayList<>();
        Main.spectator = new ArrayList<>();
        Main.builder = new ArrayList<>();
    }

    public ArrayList<EnchantingInventory> inventories;

    public static Main getInstance() {
        return Main.instance;
    }

    public void onEnable() {
        Main.instance = this;
        Main.State_Lobby = true;
        Main.State_Ending = false;
        Main.State_InGame = false;
        Main.State_DM = false;
        Main.State_Shopping = false;
        OnlineAbfrage.endet = false;
        this.loadConfig();
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getCommand("start").setExecutor(new StartCommand());
        this.getCommand("build").setExecutor(new BuildCommand());
        this.getCommand("stats").setExecutor(new StatsCommand());
        this.getCommand("rushset").setExecutor(new SetupCommands());
        this.getCommand("top").setExecutor(new Top10Command());
        final PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new RushManager(), this);
        pm.registerEvents(new BlockBreak(), this);
        pm.registerEvents(new PlayerDeath(), this);
        pm.registerEvents(new PlayerJoin(), this);
        pm.registerEvents(new PlayerQuit(), this);
        pm.registerEvents(new PlayerListener(), this);
        pm.registerEvents(new WorldListener(), this);
        pm.registerEvents(new Motd(), this);
        pm.registerEvents(new Scoreboard_DM(), this);
        pm.registerEvents(new Scoreboard_Ending(), this);
        pm.registerEvents(new Scoreboard_Shopping(), this);
        pm.registerEvents(new Scoreboard_SuchZeit(), this);
        pm.registerEvents(new Scoreboard_Lobby(), this);
        pm.registerEvents(new ChainArmor(), this);
        pm.registerEvents(new ChainArmor2(), this);
        pm.registerEvents(new IronArmor(), this);
        pm.registerEvents(new Extra(), this);
        pm.registerEvents(new Waffen(), this);
        pm.registerEvents(new Spezial(), this);
        pm.registerEvents(new Potions(), this);
        pm.registerEvents(new Perk1_Angel(), this);
        pm.registerEvents(new Perk2_InstantTNT(), this);
        pm.registerEvents(new Perk3_LuckyPotion(), this);
        pm.registerEvents(new Perk4_Staerke(), this);
        pm.registerEvents(new Perk5_Cobweb(), this);
        pm.registerEvents(new JoinItems(), this);
        pm.registerEvents(new OnlineAbfrage(), this);
        pm.registerEvents(new Restart(), this);
        pm.registerEvents(new Spectator(), this);
        pm.registerEvents(new Timer_DM(), this);
        pm.registerEvents(new Timer_Shopping(), this);
        pm.registerEvents(new Timer_SuchZeit(), this);
        pm.registerEvents(new PlayerTracker(), this);
        pm.registerEvents(new WaterJump(), this);
        pm.registerEvents(new KitSelector(), this);
        this.getCommand("build").setExecutor(new BuildCommand());
        this.getCommand("top").setExecutor(new Top10Command());
        this.getCommand("rushset").setExecutor(new SetupCommands());
        this.getCommand("rush").setExecutor(new MainCommand());
        this.getCommand("start").setExecutor(new StartCommand());
        this.getCommand("stats").setExecutor(new StatsCommand());
        this.getCommand("chest").setExecutor(new ChestCommand());
        this.getCommand("rushcoins").setExecutor(new CoinsCommand());
        MySQL.setStandardMySQL();
        MySQL.readMySQL();
        MySQL.connect();
        MySQL.createTable();

        Restart1HCountdown.on();
    }

    public void onDisable() {
        for (final Player all : Bukkit.getOnlinePlayers()) {
            all.kickPlayer("");
        }
        MySQL.close();
    }

    private void loadConfig() {
        final FileConfiguration cfg = this.getConfig();
        cfg.options().copyDefaults(true);
        this.saveConfig();
    }
}
