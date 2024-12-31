package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

import static de.Blockcraft.BungeeSystem.Data.UNBANNABLE;

public class TempBan
        extends Command {
    public TempBan() {
        super("tempban");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.ban")) {
            if (args.length < 4) {
                sender.sendMessage(Data.PREFIX + "\u00a77Ausführung:\u00a7c /tempban <Spieler> <Zeit> <Zeitform> <Grund> ");
            } else {
                if (UNBANNABLE.contains(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a74Du kannst diesen Spieler nicht bannen!");
                }
                if (BanManager.isBanned(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Dieser \u00a7cSpieler \u00a77ist bereits gebannt.");
                    return;
                }
                String TimeUnit2 = args[2];
                StringBuilder message = new StringBuilder();
                int i = 3;
                while (i < args.length) {
                    message.append(Data.FUNCTIONS.colorFormat(args[i])).append(" ");
                    ++i;
                }
                int Time = Integer.parseInt(args[1]);
                if (TimeUnit2.equalsIgnoreCase("sec") || TimeUnit2.equalsIgnoreCase("s") || TimeUnit2.equalsIgnoreCase("second") || TimeUnit2.equalsIgnoreCase("seconds") || TimeUnit2.equalsIgnoreCase("secs")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), Time);
                } else if (TimeUnit2.equalsIgnoreCase("min") || TimeUnit2.equalsIgnoreCase("minute") || TimeUnit2.equalsIgnoreCase("m") || TimeUnit2.equalsIgnoreCase("mins") || TimeUnit2.equalsIgnoreCase("minutes")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), Time * 60L);
                } else if (TimeUnit2.equalsIgnoreCase("h") || TimeUnit2.equalsIgnoreCase("hour") || TimeUnit2.equalsIgnoreCase("hours")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), (long) Time * 60 * 60);
                } else if (TimeUnit2.equalsIgnoreCase("d") || TimeUnit2.equalsIgnoreCase("day") || TimeUnit2.equalsIgnoreCase("days")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), (long) Time * 60 * 60 * 24);
                } else if (TimeUnit2.equalsIgnoreCase("w") || TimeUnit2.equalsIgnoreCase("week") || TimeUnit2.equalsIgnoreCase("weeks")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), (long) Time * 60 * 60 * 24 * 7);
                } else if (TimeUnit2.equalsIgnoreCase("mon") || TimeUnit2.equalsIgnoreCase("month") || TimeUnit2.equalsIgnoreCase("months")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), (long) Time * 60 * 60 * 24 * 30);
                } else if (TimeUnit2.equalsIgnoreCase("y") || TimeUnit2.equalsIgnoreCase("year") || TimeUnit2.equalsIgnoreCase("years")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Der Spieler \u00a7c" + args[0] + "\u00a77 wurde erfolreich gebannt.");
                    BanManager.ban(args[0], message.toString(), sender.getName(), (long) Time * 60 * 60 * 24 * 360);
                } else {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Verwende folgende Zeitformen");
                    MessageUtil.sendPlayerMessage(sender, "\u00a7c<s | m | h | d | w | mon | y>");
                }
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

