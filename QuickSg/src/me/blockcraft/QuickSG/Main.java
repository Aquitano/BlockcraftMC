package me.blockcraft.QuickSG;

import me.blockcraft.MySQL.MySQL;
import me.blockcraft.QuickSG.Commands.*;
import me.blockcraft.QuickSG.Kits.*;
import me.blockcraft.QuickSG.Listener.*;
import me.blockcraft.QuickSG.tools.*;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_Ending;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_InGame;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_Lobby;
import me.blockcraft.QuickSG.tools.items.*;
import me.eventseen.Data.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class Main
        extends JavaPlugin {
    public static final String Prefix = Data.QuickSGPrefix;
    public static final ArrayList<Player> online;
    public static final ArrayList<Player> spectator;
    public static final ArrayList<Player> builder;
    public static Main instance;
    public static boolean State_Lobby;
    public static boolean State_InGame;
    public static boolean State_DM;
    public static boolean State_Ending;
    public static boolean Schutzzeit;
    public static boolean endet;
    public static boolean countdownRunning;
    public static int countdown;

    static {
        State_Lobby = true;
        State_InGame = false;
        State_DM = false;
        State_Ending = false;
        Schutzzeit = false;
        endet = false;
        online = new ArrayList<>();
        spectator = new ArrayList<>();
        builder = new ArrayList<>();
    }

    public static Main getInstance() {
        return instance;
    }

    public static Location getSpawnPos(int i) {
        Location loc;
        String world = Main.getInstance().getConfig().getString("Pos" + i + ".World");
        double x = Main.getInstance().getConfig().getDouble("Pos" + i + ".X");
        double y = Main.getInstance().getConfig().getDouble("Pos" + i + ".Y");
        double z = Main.getInstance().getConfig().getDouble("Pos" + i + ".Z");
        float yaw = (float) Main.getInstance().getConfig().getDouble("Pos" + i + ".Yaw");
        float pitch = (float) Main.getInstance().getConfig().getDouble("Pos" + i + ".Pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public static Location getDMPos(int i) {
        Location loc;
        String world = Main.getInstance().getConfig().getString("DM" + i + ".World");
        double x = Main.getInstance().getConfig().getDouble("DM" + i + ".X");
        double y = Main.getInstance().getConfig().getDouble("DM" + i + ".Y");
        double z = Main.getInstance().getConfig().getDouble("DM" + i + ".Z");
        float yaw = (float) Main.getInstance().getConfig().getDouble("DM" + i + ".Yaw");
        float pitch = (float) Main.getInstance().getConfig().getDouble("DM" + i + ".Pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public void onEnable() {
        State_Lobby = true;
        State_InGame = false;
        State_Ending = false;
        State_DM = false;
        endet = false;
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        instance = this;
        this.loadConfig();
        TeamActionbar.start();
        Chest.addItems();
        Chest.openedchests.clear();
        this.getCommand("start").setExecutor(new StartCommand());
        this.getCommand("build").setExecutor(new BuildCommand());
        this.getCommand("stats").setExecutor(new StatsCommand());
        this.getCommand("qsgset").setExecutor(new SetupCommands());
        this.getCommand("top").setExecutor(new Top10Command());
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new LocationAPI(this), this);
        pm.registerEvents(new Motd(), this);
        pm.registerEvents(new PlayerDeath(), this);
        pm.registerEvents(new PlayerJoin(), this);
        pm.registerEvents(new PlayerListener(), this);
        pm.registerEvents(new PlayerQuit(), this);
        pm.registerEvents(new WorldListener(), this);
        pm.registerEvents(new WorldBoarder(), this);
        pm.registerEvents(new Chest(), this);
        pm.registerEvents(new OnlineAbfrage(), this);
        pm.registerEvents(new Restart(), this);
        pm.registerEvents(new Spectator(), this);
        pm.registerEvents(new TeamActionbar(), this);
        pm.registerEvents(new JoinItems(), this);
        pm.registerEvents(new Schutzzeit(), this);
        pm.registerEvents(new Countdowns(), this);
        pm.registerEvents(new EnchantmentTable(), this);
        pm.registerEvents(new FullFood(), this);
        pm.registerEvents(new HealthItem(), this);
        pm.registerEvents(new LevelUp(), this);
        pm.registerEvents(new Workbench(), this);
        pm.registerEvents(new PlayerTracker(), this);
        pm.registerEvents(new KitSelector(), this);
        pm.registerEvents(new KitSelectorItem(), this);
        pm.registerEvents(new Scoreboard_Lobby(), this);
        pm.registerEvents(new Scoreboard_InGame(), this);
        pm.registerEvents(new Scoreboard_Ending(), this);
        pm.registerEvents(new Kit1_StarterKit(), this);
        pm.registerEvents(new Kit2_Bogenkit(), this);
        pm.registerEvents(new Kit3_Herzenkit(), this);
        pm.registerEvents(new Kit4_ChainKit(), this);
        pm.registerEvents(new Kit5_DiamantKit(), this);
        MySQL.setStandardMySQL();
        MySQL.readMySQL();
        MySQL.connect();
        MySQL.createTable();

        Restart1HCountdown.on();
    }

    public void onDisable() {
        for (Player all : Bukkit.getOnlinePlayers()) {
            all.kickPlayer("");
        }
        MySQL.close();
    }

    private void loadConfig() {
        FileConfiguration cfg = this.getConfig();
        cfg.options().copyDefaults(true);
        this.saveConfig();
    }
}
