package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class MuteList
        extends Command {
    public MuteList() {
        super("mutelist");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.mutelist")) {
            if (MuteManager.getMutedPlayers().isEmpty()) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Es sind derzeit keine \u00a7cSpieler \u00a77gemuted.");
                return;
            }
            MessageUtil.sendPlayerMessage(sender, "\u00a77Diese \u00a7cSpieler \u00a77sind derzeit Gemuted:");
            for (String x : MuteManager.getMutedPlayers()) {
                MessageUtil.sendPlayerMessage(sender, "\u00a7c" + x + " \u00a78| \u00a77/check " + x);
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

