package me.blockcraft.core;

import me.blockcraft.core.Commands.*;
import me.blockcraft.core.Commands.replace.*;
import me.blockcraft.core.tools.BlockedCommandList;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    public static String Prefix;
    public static String PluginPrefix;
    public static Main instance;

    static {
        Main.Prefix = ChatColor.GOLD + "Blockcraft " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
        Main.PluginPrefix = "\u00a78[\u00a79BlockcraftCore\u00a78] \u00a77";
    }

    public static Main getInstance() {
        return Main.instance;
    }

    public void onEnable() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        Bukkit.getConsoleSender().sendMessage(Main.PluginPrefix + "Plugin erfolgreich \u00a7aaktiviert!");
        final PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new BlockedCommandList(), this);
        this.getCommand("team").setExecutor(new HelpTeamCommand());
        this.getCommand("enchant").setExecutor(new Enchantmenttable());
        this.getCommand("fly").setExecutor(new FlyCommand());
        this.getCommand("flyspeed").setExecutor(new FlySpeedCommand());
        this.getCommand("food").setExecutor(new FullFoodCommand());
        this.getCommand("gm").setExecutor(new GamemodeCommand());
        this.getCommand("heal").setExecutor(new HealCommand());
        this.getCommand("invsee").setExecutor(new Invsee());
        this.getCommand("serverinfo").setExecutor(new ServerInfoCommand());
        this.getCommand("sethealth").setExecutor(new SetHealthCommand());
        this.getCommand("skull").setExecutor(new SkullCommand());
        this.getCommand("speed").setExecutor(new SpeedCommand());
        this.getCommand("sudo").setExecutor(new SudoCommand());
        this.getCommand("kill").setExecutor(new KillCommand());
        this.getCommand("rain").setExecutor(new RainCommand());
        this.getCommand("sun").setExecutor(new SunCommand());
        this.getCommand("time").setExecutor(new TimeCommand());
        this.getCommand("tp").setExecutor(new TpCommand());
        this.getCommand("xp").setExecutor(new XPCommand());
        this.getCommand("msg").setExecutor(new MSGCommand());
        this.getCommand("v").setExecutor(new VanishCommand());
        this.getCommand("clear").setExecutor(new ClearCommand());
    }

    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(Main.PluginPrefix + "Plugin erfolgreich \u00a7cdeaktiviert!");
    }
}
