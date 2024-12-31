package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class AddPremium
        extends Command {
    public AddPremium() {
        super("addpremium");
    }

    public void execute(CommandSender sender, String[] args) {
        ProxiedPlayer p = (ProxiedPlayer) sender;
        if (args.length == 1) {
            String pl = args[0];
            ProxiedPlayer pp = ProxyServer.getInstance().getPlayer(pl);
            pp.addGroups("Premium");
            MessageUtil.sendPlayerMessage(p, "\u00a73Du hast die \u00a7eBerechtung \u00a73erfolgreich hinzugefügt.");
            if (pp.hasPermission("bs.vip")) {
                MessageUtil.sendPlayerMessage(p, "\u00a7e" + pp.getName() + "\u00a73 hat nun die \u00a7eRechte\u00a73.");
            }
        }
    }
}

