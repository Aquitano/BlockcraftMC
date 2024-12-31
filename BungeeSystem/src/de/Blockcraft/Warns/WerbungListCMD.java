package de.Blockcraft.Warns;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Config;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

import java.util.Objects;

public class WerbungListCMD
        extends Command {
    public WerbungListCMD() {
        super("werbung");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.werbungmute")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("clear")) {
                    MessageUtil.sendPlayerMessage(sender, Config.Config.Werbungsserver.size() + " Server wurden gefunden.");
                    Config.Config.Werbungsserver.clear();
                    Config.saveConfig();
                    return;
                }
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/werbunglist [clear]");
            } else if (Config.Config.Werbungsserver.size() < 19) {
                MessageUtil.sendPlayerMessage(sender, ChatColor.DARK_AQUA + "---------" + ChatColor.GOLD + "Es sind " + Config.Config.Werbungsserver.size() + " Server gefunden." + ChatColor.DARK_AQUA + "----------");
                String OnlinePlayers = "";
                for (String s : Config.Config.Werbungsserver) {
                    OnlinePlayers = Objects.equals(OnlinePlayers, "") ? s.split(" ")[0] : OnlinePlayers + "," + s.split(" ")[0];
                }
                MessageUtil.sendPlayerMessage(sender, ChatColor.DARK_RED + OnlinePlayers);
            } else {
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Die Server können nicht angezeigt werden. Grund:" + ChatColor.YELLOW + " Zu viele Server.");
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

