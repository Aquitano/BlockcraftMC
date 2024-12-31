package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class Kick
        extends Command {
    public Kick() {
        super("kick");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        ProxiedPlayer p = (ProxiedPlayer) sender;
        if (sender.hasPermission("bs.kick")) {
            if (args.length < 2) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Verwende \u00a7c/kick <Spieler> <Grund>");
            } else {
                if (ProxyServer.getInstance().getPlayer(args[0]) == null) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Dieser \u00a7cSpieler \u00a77ist nicht online.");
                    return;
                }
                StringBuilder message = new StringBuilder();
                int i = 1;
                while (i < args.length) {
                    message.append(args[i]).append(" ");
                    ++i;
                }
                MessageUtil.sendPlayerMessage(sender, "\u00a77Du hast den \u00a7cSpieler \u00a77erfolgreich vom Netzwerk geworfen.");
                ProxiedPlayer p2 = ProxyServer.getInstance().getPlayer(args[0]);
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    if (!all.hasPermission("bs.kick")) continue;
                    MessageUtil.sendPlayerMessage(sender, "\u00a7c" + p2.getName() + "\u00a77 wurde von \u00a7c" + p.getName() + "\u00a77 vom \u00a73" + Data.IP + " Netzwerk \u00a77gekickt.");
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Grund: \u00a7a" + message);
                }
                String reason = message.toString();
                p2.disconnect("\u00a77Du wurdest vom \u00a73" + Data.IP + " Netzwerk \u00a77gekickt. \n \u00a77Grund: \u00a7a" + reason);
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

