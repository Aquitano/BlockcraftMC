package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class UnBan
        extends Command {
    public UnBan() {
        super("unban");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.ban")) {
            if (args.length != 1) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Ausführung:\u00a7c /unban <Spieler>");
            } else {
                if (!BanManager.isBanned(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Dieser \u00a7cSpieler \u00a77ist nicht gebannt.");
                    return;
                }
                MessageUtil.sendPlayerMessage(sender, "\u00a77Der \u00a7cSpieler \u00a77wurde entbannt.");
                BanManager.unBan(args[0], sender.getName());
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

