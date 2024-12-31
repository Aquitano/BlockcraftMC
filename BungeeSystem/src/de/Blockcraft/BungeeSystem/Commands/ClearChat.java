package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class ClearChat
        extends Command {

    public ClearChat(String cmd) {
        super(cmd);
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        ProxiedPlayer p = (ProxiedPlayer) sender;
        if (sender.hasPermission("bs.clearchat")) {
            if (args.length != 0) {
                MessageUtil.sendPlayerMessage(sender, "\u00a77Verwende \u00a7c/cc");
            } else {
                int i = 0;
                while (i < 200) {
                    for (ProxiedPlayer all : p.getServer().getInfo().getPlayers()) {
                        all.sendMessage(" ");
                    }
                    ++i;
                }
                for (ProxiedPlayer all : p.getServer().getInfo().getPlayers()) {
                    MessageUtil.sendPlayerMessage(all, "\u00a77Der \u00a7aChat \u00a77wurde geleert!");
                }
            }
        } else {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
        }
    }
}

