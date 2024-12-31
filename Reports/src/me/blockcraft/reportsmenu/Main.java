package me.blockcraft.reportsmenu;

import me.blockcraft.reportsmenu.commands.ReportsCommand;
import me.blockcraft.reportsmenu.listeners.InvClickListener;
import me.blockcraft.reportsmenu.listeners.JoinListeners;
import me.blockcraft.reportsmenu.util.FileManager;
import me.blockcraft.reportsmenu.util.MySQL;
import me.blockcraft.reportsmenu.util.ReportsManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    public static Main instance;
    public final String prefix;

    public Main() {
        this.prefix = ChatColor.RED + "Reports " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
    }

    public static Main getInstance() {
        return Main.instance;
    }

    public void onEnable() {
        (Main.instance = this).registerCommands();
        this.registerEvents();
        FileManager.createStandardConfig();
        FileManager.fetchData();
        MySQL.Connect();
        MySQL.createTable();
        System.out.println("[ReportsMenu] Plugin erfolgreich aktiviert!");
        Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, () -> {
            if (MySQL.isConnected()) {
                System.out.println("[Reports-SQL] Es gibt aktuell " + ReportsManager.getReportLists("Reportedname").size() + " Reports!");
                System.out.println("[Reports-SQL] MySQL Verbindung aktualisiert! ");
            }
        }, 0L, 36000L);
    }

    public void onDisable() {
        MySQL.close();
        System.out.println("[ReportsMenu] Plugin erfolgreich deaktiviert!");
    }

    public void registerCommands() {
        this.getCommand("reports").setExecutor(new ReportsCommand(this));
    }

    public void registerEvents() {
        this.getServer().getPluginManager().registerEvents(new InvClickListener(), this);
        this.getServer().getPluginManager().registerEvents(new JoinListeners(this), this);
    }
}
