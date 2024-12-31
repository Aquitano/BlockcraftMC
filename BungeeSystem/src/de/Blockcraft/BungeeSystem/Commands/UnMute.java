package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

public class UnMute
        extends Command {
    public UnMute() {
        super("unmute");
    }

    public void execute(CommandSender sender, String[] args) {
        if (sender.hasPermission("bs.mute")) {
            if (args.length < 1) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Ausführung:\u00a7c /unmute <Spieler>");
            } else {
                if (!MuteManager.isMuted(args[0])) {
                    MessageUtil.sendPlayerMessage(sender, "\u00a77Dieser \u00a7cSpieler\u00a77 ist nicht gemuted.");
                    return;
                }
                MessageUtil.sendPlayerMessage(sender, "\u00a77Dieser \u00a7cSpieler \u00a77wurde erfolgreich entmuted.");
                MuteManager.unMute(args[0], sender.getName());
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

