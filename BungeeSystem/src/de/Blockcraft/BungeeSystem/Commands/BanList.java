package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class BanList
        extends Command {
    public BanList() {
        super("banlist");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.banlist")) {
            if (BanManager.getBannedPlayers().isEmpty()) {
                MessageUtil.sendPlayerMessage(sender, "\u00a7eDerzeit sind keine Spieler gebannt.");
                return;
            }
            MessageUtil.sendPlayerMessage(sender, "\u00a76Folgende Spieler sind gebannt: ");
            for (String x : BanManager.getBannedPlayers()) {
                MessageUtil.sendPlayerMessage(sender, "\u00a7c" + x + " \u00a78| \u00a77/check\u00a7c " + x);
            }
            sender.sendMessage("");
            MessageUtil.sendPlayerMessage(sender, "\u00a76Gebannte Spieler: \u00a73" + BanManager.getBannedPlayers().size());

        } else {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cDir fehlt dafür die Berechtigung!");
        }
    }
}

