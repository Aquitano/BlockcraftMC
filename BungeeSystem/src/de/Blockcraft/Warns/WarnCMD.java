package de.Blockcraft.Warns;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Config;
import de.Blockcraft.BungeeSystem.MOTD.Manager_Chat;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.Optional;

public class WarnCMD
        extends Command {
    private String LastWarn = "- -";

    public WarnCMD() {
        super("warn");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.warn")) {
            if (args.length == 0) {
                MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "\u00a7c/warn <player> [reason]");
            } else {
                ProxiedPlayer p = null;
                for (ProxiedPlayer pl : ProxyServer.getInstance().getPlayers()) {
                    if (!pl.getName().equalsIgnoreCase(args[0])) continue;
                    if (pl.hasPermission("bs.warn")) {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "\u00a77Du kannst diesen\u00a7c Spieler\u00a77 nicht warnen.");
                        return;
                    }
                    p = pl;
                    break;
                }
                if (sender instanceof ProxiedPlayer && sender.getName().equalsIgnoreCase(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "\u00a77Du kannst dich nicht selbst warnen!");
                    return;
                }
                if (p == null) {
                    if (args[0].endsWith("*")) {
                        if (!this.LastWarn.equals(sender.getName() + " " + args[0].toLowerCase())) {
                            this.askWarn(sender, args[0].replace("*", ""), null);
                            this.LastWarn = sender.getName() + " " + args[0].toLowerCase();
                        }
                        return;
                    }
                    this.LastWarn = "- -";
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "\u00a77Der \u00a7cSpieler\u00a77 wurde nicht gefunden oder ist offline! ");
                    if (sender instanceof ProxiedPlayer) {
                        sender.sendMessage(Manager_Chat.getComponent(null, ChatColor.GREEN + "Hier klicken", ChatColor.RED + ", um \"" + ChatColor.GOLD + args[0] + ChatColor.RED + "\" trotzdem zu warnen.", new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warn " + args[0] + "*"), ChatColor.GREEN + args[0] + " trotzdem warnen..."));
                    }
                    return;
                }
                StringBuilder Message = null;
                if (args.length > 1) {
                    Message = new StringBuilder();
                    int count = 1;
                    while (count < args.length) {
                        Message.append(" ").append(args[count]);
                        count = count + 1;
                    }
                    if ((Message = Optional.ofNullable(Manager_Chat.getMessage(sender, Message.toString())).map(StringBuilder::new).orElse(null)) == null) {
                        return;
                    }
                }
                this.askWarn(sender, p.getName(), Message == null ? null : Message.toString());
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }

    @SuppressWarnings("deprecation")
    public void askWarn(CommandSender sender, String playerName, String reason) {
        if (this.Warn(sender, playerName, reason)) {
            for (String bp : Config.Config.BannedPlayers) {
                if (bp.length() < 3 || !playerName.equalsIgnoreCase(bp.split(" ")[0])) continue;
                return;
            }
            sender.sendMessage("");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Der Spieler " + ChatColor.GOLD + playerName + ChatColor.RED + " sollte jetzt, wegen");
            MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "zuvielen " + ChatColor.YELLOW + "Warns" + ChatColor.DARK_RED + " gebannt " + ChatColor.RED + "werden.");
            sender.sendMessage(Manager_Chat.getComponent(null, ChatColor.GREEN + "Hier klicken", ChatColor.RED + ", um das Bannen von \"" + ChatColor.GOLD + playerName + ChatColor.RED + "\" zu bestätigen.", new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ban " + playerName + "**"), ChatColor.GREEN + playerName + " vom Server bannen..."));
        }
    }

    @SuppressWarnings("deprecation")
    public Boolean Warn(CommandSender who, String playerName, String reason) {
        Manager_Warns.addWarn(playerName);
        Integer warns = Manager_Warns.getWarns(playerName);
        ProxiedPlayer p = ProxyServer.getInstance().getPlayer(playerName);
        if (reason == null) {
            ProxyServer.getInstance().broadcast(ChatColor.DARK_AQUA + "Der Spieler " + ChatColor.RED + ChatColor.BOLD + "\"" + playerName + "\"" + ChatColor.DARK_AQUA + " bekam einen " + ChatColor.GOLD + "Warn " + ChatColor.DARK_AQUA + "(" + ChatColor.YELLOW + warns + ChatColor.DARK_AQUA + "/" + ChatColor.YELLOW + Config.Config.MaxWarns + ChatColor.DARK_AQUA + ").");
        } else {
            ProxyServer.getInstance().broadcast(ChatColor.DARK_AQUA + "Der Spieler " + ChatColor.RED + ChatColor.BOLD + "\"" + playerName + "\"" + ChatColor.DARK_AQUA + " bekam einen " + ChatColor.GOLD + "Warn " + ChatColor.DARK_AQUA + "(" + ChatColor.YELLOW + warns + ChatColor.DARK_AQUA + "/" + ChatColor.YELLOW + Config.Config.MaxWarns + ChatColor.DARK_AQUA + "). " + ChatColor.RED + "Grund: " + ChatColor.YELLOW + reason);
        }
        if (p != null) {
            MessageUtil.sendPlayerMessage(p, ChatColor.GOLD + "Du hast " + ChatColor.RED + warns + ChatColor.GOLD + "/" + ChatColor.GOLD + Config.Config.MaxWarns + ChatColor.RED + " Warns.");
            if (warns >= Config.Config.MaxWarns) {
                p.disconnect(ChatColor.RED + "Du wurdest wegen " + ChatColor.GOLD + warns + ChatColor.RED + "/" + ChatColor.RED + Config.Config.MaxWarns + ChatColor.GOLD + " Warns vom Server geworfen.");
            }
        }
        return warns >= Config.Config.MaxWarns;
    }
}

