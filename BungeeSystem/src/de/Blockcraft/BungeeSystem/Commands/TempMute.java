package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class TempMute
        extends Command {
    public TempMute() {
        super("tempmute");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.mute")) {
            if (args.length < 4) {
                MessageUtil.sendPlayerMessage(sender, "\u00a7cAusführung: /tempmute <Spieler> <Zeit> <Zeitform> <Grund> ");
            } else {
                if (MuteManager.isMuted(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a7cDieser \u00a7cSpieler \u00a7cist bereits gemuted.");
                    return;
                }
                String TimeUnit2 = args[2];
                StringBuilder message = new StringBuilder();
                int i = 3;
                while (i < args.length) {
                    message.append(args[i]).append(" ");
                    ++i;
                }
                int Time = Integer.parseInt(args[1]);
                if (TimeUnit2.equalsIgnoreCase("sec") || TimeUnit2.equalsIgnoreCase("s") || TimeUnit2.equalsIgnoreCase("second") || TimeUnit2.equalsIgnoreCase("seconds") || TimeUnit2.equalsIgnoreCase("secs")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Du hast den Spieler \u00a7c" + args[0] + "\u00a77 erfolgreich gemuted.");
                    MuteManager.mute(args[0], message.toString(), sender.getName(), Time);
                } else if (TimeUnit2.equalsIgnoreCase("min") || TimeUnit2.equalsIgnoreCase("minute") || TimeUnit2.equalsIgnoreCase("m") || TimeUnit2.equalsIgnoreCase("mins") || TimeUnit2.equalsIgnoreCase("minutes")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Du hast den Spieler \u00a7c" + args[0] + "\u00a77 erfolgreich gemuted.");
                    MuteManager.mute(args[0], message.toString(), sender.getName(), Time * 60);
                } else if (TimeUnit2.equalsIgnoreCase("h") || TimeUnit2.equalsIgnoreCase("hour") || TimeUnit2.equalsIgnoreCase("hours")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Du hast den Spieler \u00a7c" + args[0] + "\u00a77 erfolgreich gemuted.");
                    MuteManager.mute(args[0], message.toString(), sender.getName(), Time * 60 * 60);
                } else if (TimeUnit2.equalsIgnoreCase("d") || TimeUnit2.equalsIgnoreCase("day") || TimeUnit2.equalsIgnoreCase("days")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Du hast den Spieler \u00a7c" + args[0] + "\u00a77 erfolgreich gemuted.");
                    MuteManager.mute(args[0], message.toString(), sender.getName(), Time * 60 * 60 * 24);
                } else if (TimeUnit2.equalsIgnoreCase("w") || TimeUnit2.equalsIgnoreCase("week") || TimeUnit2.equalsIgnoreCase("weeks")) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Du hast den Spieler \u00a7c" + args[0] + "\u00a77 erfolgreich gemuted.");
                    MuteManager.mute(args[0], message.toString(), sender.getName(), Time * 60 * 60 * 24 * 7);
                } else {
                    MessageUtil.sendPlayerMessage(sender, "\u00a7cVerwende folgende Zeitformen");
                    MessageUtil.sendPlayerMessage(sender, "\u00a7c< s | m | h | d | w >");
                }
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

