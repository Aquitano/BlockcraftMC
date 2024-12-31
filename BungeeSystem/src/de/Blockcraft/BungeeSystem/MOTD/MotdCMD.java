package de.Blockcraft.BungeeSystem.MOTD;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

import java.util.Objects;

public class MotdCMD
        extends Command {
    public MotdCMD(String string) {
        super(string);
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.motd")) {
            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("list")) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.DARK_AQUA + "----------" + ChatColor.GOLD + "Motds" + ChatColor.DARK_AQUA + "----------");
                    MessageUtil.sendPlayerMessage(sender, ChatColor.BLUE + "Default-Motd: " + ChatColor.GRAY + Config.Config.DefaultMotd.replace("&", "\u00a7"));
                    sender.sendMessage("");
                    String OnlinePlayers = "";
                    int count = 1;
                    for (String s : Config.Config.RandomMotds) {
                        MessageUtil.sendPlayerMessage(sender, count + ": " + ChatColor.GRAY + s.replace("&", "\u00a7"));
                        count = count + 1;
                    }
                    if (count == 1) {
                        MessageUtil.sendPlayerMessage(sender, "-");
                    }
                    return;
                }
                if (args[0].equalsIgnoreCase("clear")) {
                    Integer i = Config.Config.RandomMotds.size();
                    Config.Config.RandomMotds.clear();
                    if (!Config.saveConfig()) {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Fehler: java.lang.file_write_error!");
                        return;
                    }
                    Listener_Motds.LoadMotds();
                    MessageUtil.sendPlayerMessage(sender, i + " Random-Motds wurden erfolgreich gel\u00f6scht!");
                    return;
                }
            }
            if (args.length <= 1) {
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd setdefault <motd>");
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd setnewbie <motd>");
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd add <motd>");
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd del <motd>");
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd clear");
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd list");
                return;
            }
            if (args[0].equalsIgnoreCase("setdefault")) {
                String message = "";
                int count = 1;
                while (count < args.length) {
                    message = !Objects.equals(message, "") ? message + " " + args[count] : args[count];
                    count = count + 1;
                }
                Config.Config.DefaultMotd = message;
                if (!Config.saveConfig()) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Fehler: java.lang.file_write_error!");
                    return;
                }
                Listener_Motds.LoadMotds();
                MessageUtil.sendPlayerMessage(sender, ChatColor.GOLD + "Die Default-Motd wurde erfolgreich zu \"" + ChatColor.GRAY + message.replace("&", "\u00a7") + ChatColor.GOLD + "\" ge\u00e4ndert!");
                return;
            }
            if (args[0].equalsIgnoreCase("add")) {
                String message = "";
                int count = 1;
                while (count < args.length) {
                    message = !Objects.equals(message, "") ? message + " " + args[count] : args[count];
                    count = count + 1;
                }
                Config.Config.RandomMotds.add(message);
                if (!Config.saveConfig()) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Fehler: java.lang.file_write_error!");
                    return;
                }
                Listener_Motds.LoadMotds();
                MessageUtil.sendPlayerMessage(sender, ChatColor.GOLD + "Die Motd \"" + ChatColor.GRAY + message.replace("&", "\u00a7") + ChatColor.GOLD + "\" wurde erfolgreich hinzugef\u00fcgt!");
                return;
            }
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("del")) {
                int remove;
                try {
                    remove = Integer.parseInt(args[1]);
                    if (remove <= 0) {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd del <zahl 1-" + Config.Config.RandomMotds.size() + ">");
                        return;
                    }
                    if (remove > Config.Config.RandomMotds.size()) {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd del <zahl 1-" + Config.Config.RandomMotds.size() + ">");
                        return;
                    }
                } catch (Exception e) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd del <zahl>");
                    return;
                }
                MessageUtil.sendPlayerMessage(sender, ChatColor.GOLD + "Die Motd \"" + ChatColor.GRAY + Config.Config.RandomMotds.get(remove - 1).replace("&", "\u00a7") + ChatColor.GOLD + "\" wurde erfolgreich gel\u00a7scht!");
                Config.Config.RandomMotds.remove(remove - 1);
                if (!Config.saveConfig()) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Fehler: java.lang.file_write_error!");
                    return;
                }
                Listener_Motds.LoadMotds();
                return;
            }
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd setdefault <motd>");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd setnewbie <motd>");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd add <motd>");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd del <zahl>");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd clear");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/motd list");
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

