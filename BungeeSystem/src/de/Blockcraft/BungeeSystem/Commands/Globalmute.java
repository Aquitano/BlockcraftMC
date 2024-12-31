package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class Globalmute
        extends Command {

    public Globalmute() {
        super("noregistred");
    }

    public void execute(CommandSender sender, String[] args) {
        //ProxiedPlayer p = (ProxiedPlayer)sender;
        if (sender.hasPermission("bs.gmute")) {
            MessageUtil.sendPlayerMessage(sender, "\u00a77Verwende \u00a7c/rank mute");
        } else {
            MessageUtil.sendPlayerMessage(sender, "\u00a7c");
        }
    }
}

