package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class Check
        extends Command {
    public Check() {
        super("check");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.check")) {
            if (args.length == 0) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Ausführung: \u00a7c/check <Spieler>");
            } else {

                if (BanManager.isBanned(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a72\u00a7m---------------------------------");
                    sender.sendMessage("");
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eSpieler: \u00a7a" + args[0]);
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eGebannt von: \u00a73" + BanManager.getWhoBanned(args[0]));
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund: \u00a73" + BanManager.getReason(args[0]));
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eVerbleibende Zeit: \u00a73" + BanManager.getRemainingTime(args[0]));
                    sender.sendMessage("");
                } else {
                    MessageUtil.sendPlayerMessage(sender, "\u00a72\u00a7m---------------------------------");
                    sender.sendMessage("");
                    MessageUtil.sendPlayerMessage(sender, "\u00a76Der Spieler ist nicht gebannt.");
                    sender.sendMessage("");
                }
                if (MuteManager.isMuted(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a73\u00a7m---------------------------------");
                    sender.sendMessage("");
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eSpieler: \u00a7a" + args[0]);
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eGemuted von: \u00a73" + MuteManager.getWhoMuted(args[0]));
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund: \u00a73" + MuteManager.getReason(args[0]));
                    MessageUtil.sendPlayerMessage(sender, "\u00a7eVerbleibende Zeit: \u00a73" + MuteManager.getRemainingTime(args[0]));
                    sender.sendMessage("");
                    MessageUtil.sendPlayerMessage(sender, "\u00a72\u00a7m---------------------------------");
                } else {
                    MessageUtil.sendPlayerMessage(sender, "\u00a73\u00a7m---------------------------------");
                    sender.sendMessage("");
                    MessageUtil.sendPlayerMessage(sender, "\u00a76Der Spieler ist nicht gemuted.");
                    sender.sendMessage("");
                    MessageUtil.sendPlayerMessage(sender, "\u00a72\u00a7m---------------------------------");
                    sender.sendMessage("");
                }
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

