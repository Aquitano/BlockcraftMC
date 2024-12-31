package me.blockcraft.perms;

import me.blockcraft.perms.Listeners.ChatListener;
import me.blockcraft.perms.Listeners.JoinListener;
import me.blockcraft.perms.Methods.Manager;
import me.blockcraft.perms.MySQL.API;
import me.blockcraft.perms.MySQL.MySQL;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {
    public static int i;
    public static String PluginPrefix;
    public static LuckPerms luckPerms;

    static {
        Main.i = 500;
        Main.PluginPrefix = "\u00a78[\u00a79BlockcraftPerms\u00a78] \u00a77";
    }

    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage(PluginPrefix + "Plugin gestartet");

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            Bukkit.getConsoleSender().sendMessage(PluginPrefix + "LuckPerms API gefunden");
        } else {
            Bukkit.getConsoleSender().sendMessage(PluginPrefix + "LuckPerms API nicht gefunden");
        }
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(new ChatListener(), this);
        this.getServer().getPluginManager().registerEvents(new JoinListener(), this);
        MySQL.setStandardMySQL();
        MySQL.readMySQL();
        MySQL.connect();
        MySQL.createTable();
    }

    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(PluginPrefix + "Plugin gestoppt");
        MySQL.close();
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String commandLabel, final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7cDieser Command kann nur von einem Spieler ausgeführt werden.");
            return true;
        }

        final Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("set")) {
            if (p.hasPermission("Permission.user")) {
                if (args.length != 2) {
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7cVerwendung: /set <Spieler> <Rang>");
                    return true;
                }

                final Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7cDieser Spieler ist nicht online.");
                    return true;
                }

                String rank = args[1];
                boolean validRank = true;

                switch (rank.toLowerCase()) {
                    case "admin":
                        API.setAdmin(target.getUniqueId().toString(), target.getName());
                        p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Der Spieler\u00a7e " + target.getName() + "\u00a77 wurde der Gruppe \u00a7eAdmin \u00a77hinzugefügt.");
                        break;
                    case "dev":
                        API.setDev(target.getUniqueId().toString(), target.getName());
                        p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Der Spieler\u00a7e " + target.getName() + "\u00a77 wurde der Gruppe \u00a7eDev \u00a77hinzugefügt.");
                        break;
                    case "yt":
                        API.setYT(target.getUniqueId().toString(), target.getName());
                        p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Der Spieler\u00a7e " + target.getName() + "\u00a77 wurde der Gruppe \u00a7eYouTuber \u00a77hinzugefügt.");
                        break;
                    case "premium":
                        API.setPremium(target.getUniqueId().toString(), target.getName());
                        p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Der Spieler\u00a7e " + target.getName() + "\u00a77 wurde der Gruppe \u00a7ePremium \u00a77hinzugefügt.");
                        break;
                    case "owner":
                        API.setOwner(target.getUniqueId().toString(), target.getName());
                        p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Der Spieler\u00a7e " + target.getName() + "\u00a77 wurde der Gruppe \u00a7eOwner \u00a77hinzugefügt.");
                        break;
                    case "spieler":
                        API.setPlayer(target.getUniqueId().toString(), target.getName());
                        p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Der Spieler\u00a7e " + target.getName() + "\u00a77 wurde der Gruppe \u00a7eSpieler \u00a77hinzugefügt.");
                        break;
                    default:
                        validRank = false;
                        break;
                }

                if (validRank) {
                    Manager.refreshPlayer(target);
                } else {
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a77Verfügbare Gruppen:");
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7e- \u00a77Admin");
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7e- \u00a77Dev");
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7e- \u00a77YT");
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7e- \u00a77Premium");
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7e- \u00a77Owner");
                    p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7e- \u00a77Spieler");
                }

                return true;
            } else {
                p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7cDu verfügst nicht über die Berrechtigung diesen Command zu nutzen.");
                return true;
            }
        }
        return false;
    }
}
