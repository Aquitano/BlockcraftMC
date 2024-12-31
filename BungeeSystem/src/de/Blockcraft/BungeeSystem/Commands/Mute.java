package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class Mute
        extends Command {
    public Mute() {
        super("mute");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.mute")) {
            MessageUtil.sendPlayerMessage(sender, "\u00a77Nutze \u00a7c/strafe <Name> <Grund>");
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

