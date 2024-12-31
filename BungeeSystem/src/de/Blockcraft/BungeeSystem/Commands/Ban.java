package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class Ban
        extends Command {
    public Ban() {
        super("Ban");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.ban")) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cNutze Folgendes: \u00a73/strafe");
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

