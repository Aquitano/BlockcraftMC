package de.Blockcraft.BungeeSystem.MOTD;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class SetMaxCMD
        extends Command {
    public SetMaxCMD(String string) {
        super(string);
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.setmaxplayers")) {
            if (args.length == 1) {
                try {
                    Config.Config.MaxPlayers = Integer.valueOf(args[0]);
                    if (!Config.saveConfig()) {
                        MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "Fehler: java.lang.file_write_error!");
                        return;
                    }
                    MessageUtil.sendPlayerMessage(sender, ChatColor.GOLD + "\u00a77Die maximale \u00a7bSpieleranzahl\u00a77 wurde zu \u00a7b" + args[0] + ChatColor.GOLD + "\u00a77 gesetzt!");
                } catch (Exception ex) {
                    MessageUtil.sendPlayerMessage(sender, ChatColor.RED + "\u00a7b/setmax [maxplayers]");
                }
            } else {
                MessageUtil.sendPlayerMessage(sender, ChatColor.GOLD + "\u00a77Es k\u00f6nnen maximal \u00a7b" + Config.Config.MaxPlayers + ChatColor.GOLD + "\u00a77 Spieler online!");
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

