package de.Blockcraft.Warns;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Config;
import de.Blockcraft.BungeeSystem.MOTD.Manager_Chat;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.plugin.Command;

public class WarnsCMD
        extends Command {
    public WarnsCMD() {
        super("warns");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.warns")) {
            if (args.length >= 1) {
                if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("reset")) {
                    if (args.length == 2) {
                        sender.sendMessage(Manager_Chat.getComponent(null, ChatColor.GREEN + "Hier klicken", ChatColor.RED + ", um das Zurücksetzen der Warns von", new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warns clear* " + args[1]), ChatColor.GREEN + "Alle Warns zurücksetzen..."));
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "\"" + ChatColor.GOLD + args[1] + ChatColor.RED + "\" zu best\ufffdtigen.");
                    } else if (args.length == 1) {
                        sender.sendMessage(Manager_Chat.getComponent(null, ChatColor.GREEN + "Hier klicken", ChatColor.RED + ", um das Zurücksetzen aller Warns zu bestätigen.", new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warns clear*"), ChatColor.GREEN + "Alle Warns zurücksetzen..."));
                    } else {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/warns <clear|get|list> [player]");
                    }
                    return;
                }
                if (args[0].equalsIgnoreCase("clear*")) {
                    if (args.length == 2) {
                        Integer oldWarns = Manager_Warns.getWarns(args[1]);
                        Manager_Warns.setWarns(args[1], 0);
                        MessageUtil.sendPlayerMessage(sender, ChatColor.GREEN + "Die " + ChatColor.YELLOW + "Warns (" + oldWarns + " Warns) " + ChatColor.GREEN + "von \"" + ChatColor.GOLD + args[1] + ChatColor.GREEN + "\", wurden erfolgreich resetet.");
                    }
                    if (args.length == 1) {
                        Config.Config.PlayerWarns.clear();
                        if (!Config.saveConfig()) {
                            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Fehler: java.lang.file_write_error!");
                            return;
                        }
                        MessageUtil.sendPlayerMessage(sender, ChatColor.GREEN + "Alle Warns wurden erfolgreich resetet.");
                    }
                    return;
                }
                if (args[0].equalsIgnoreCase("get")) {
                    if (args.length == 2) {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.GREEN + "Verwarnungen von " + ChatColor.GOLD + args[1] + ChatColor.GREEN + ": " + ChatColor.YELLOW + Manager_Warns.getWarns(args[1]) + ChatColor.GREEN + ".");
                    } else {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/warns <clear|get|list> [player]");
                    }
                    return;
                }
                if (args[0].equalsIgnoreCase("list")) {
                    if (Manager_Warns.listWarns().size() < 19) {
                        sender.sendMessage(ChatColor.DARK_AQUA + "----------" + ChatColor.GOLD + "Verwarnungen (" + Manager_Warns.listWarns().size() + ")" + ChatColor.DARK_AQUA + "----------");
                        for (String s : Manager_Warns.listWarns()) {
                            if (!s.contains(" ")) continue;
                            MessageUtil.sendPlayerMessage(sender, ChatColor.YELLOW + "  " + s.split(" ")[0] + ": " + ChatColor.RED + s.split(" ")[1]);
                        }
                    } else {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Die Spieler können nicht angezeigt werden. Grund:" + ChatColor.YELLOW + " Zu viele Spieler.");
                    }
                    return;
                }
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/warns <clear|get|list> [player]");
            } else {
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "/warns <clear|get|list> [player]");
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

